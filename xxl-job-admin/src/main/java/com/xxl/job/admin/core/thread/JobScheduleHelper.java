package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.cron.CronExpression;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.scheduler.MisfireStrategyEnum;
import com.xxl.job.admin.core.scheduler.ScheduleTypeEnum;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author xuxueli 2019-05-21
 */
public class JobScheduleHelper {
    public static final long PRE_READ_MS = 5000;    // pre read
    public static final String JOB_REDIS_LOCK = "job:lock:";
    private static Logger logger = LoggerFactory.getLogger(JobScheduleHelper.class);
    private static JobScheduleHelper instance = new JobScheduleHelper();
    private volatile static Map<Integer, List<Integer>> ringData = new ConcurrentHashMap<>();
    private Thread scheduleThread;
    private Thread ringThread;
    private volatile boolean scheduleThreadToStop = false;
    private volatile boolean ringThreadToStop = false;

    public static JobScheduleHelper getInstance() {
        return instance;
    }

    // ---------------------- tools ----------------------
    public static Date generateNextValidTime(XxlJobInfo jobInfo, Date fromTime) throws Exception {
        ScheduleTypeEnum scheduleTypeEnum = ScheduleTypeEnum.match(jobInfo.getScheduleType(), null);
        if (ScheduleTypeEnum.CRON == scheduleTypeEnum) {
            Date nextValidTime = new CronExpression(jobInfo.getScheduleConf()).getNextValidTimeAfter(fromTime);
            return nextValidTime;
        } else if (ScheduleTypeEnum.FIX_RATE == scheduleTypeEnum /*|| ScheduleTypeEnum.FIX_DELAY == scheduleTypeEnum*/) {
            return new Date(fromTime.getTime() + Integer.valueOf(jobInfo.getScheduleConf()) * 1000);
        }
        return null;
    }

    public void start() {

        // schedule thread ???????????????????????????
        scheduleThread = new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    TimeUnit.MILLISECONDS.sleep(5000 - System.currentTimeMillis() % 1000);
                } catch (InterruptedException e) {
                    if (!scheduleThreadToStop) {
                        logger.error(e.getMessage(), e);
                    }
                }
                logger.info(">>>>>>>>> init xxl-job admin scheduler success.");

                // pre-read count: treadpool-size * trigger-qps (each trigger cost 50ms, qps = 1000/50 = 20)
                int preReadCount = (XxlJobAdminConfig.getAdminConfig().getTriggerPoolFastMax() + XxlJobAdminConfig.getAdminConfig().getTriggerPoolSlowMax()) * 20;
                //TODO ????????????redis???????????????
                StringRedisTemplate redisTemplate = XxlJobAdminConfig.getAdminConfig().getRedisTemplate();
                while (!scheduleThreadToStop) {
                    // Scan Job ????????????
                    long start = System.currentTimeMillis();
                    try {
                        // 1???pre read ??????5????????????????????????
                        long nowTime = System.currentTimeMillis();
                        List<XxlJobInfo> scheduleList = XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().scheduleJobQuery(nowTime + PRE_READ_MS, preReadCount);
                        if (scheduleList != null && scheduleList.size() > 0) {
                            // 2???push time-ring
                            for (XxlJobInfo jobInfo : scheduleList) {
                                String uuid = UUID.randomUUID().toString();
                                Boolean lock = redisTemplate.opsForValue().setIfAbsent(JOB_REDIS_LOCK + jobInfo.getId(), uuid, 30, TimeUnit.SECONDS);
                                if (lock) {
                                    try {
                                        // time-ring jump
                                        if (nowTime > jobInfo.getTriggerNextTime() + PRE_READ_MS) {
                                            // 2.1???trigger-expire > 5s???pass && make next-trigger-time  ????????????????????????5???
                                            logger.warn(">>>>>>>>>>> xxl-job, schedule misfire, jobId = " + jobInfo.getId());

                                            // 1???misfire match ????????????????????????
                                            MisfireStrategyEnum misfireStrategyEnum = MisfireStrategyEnum.match(jobInfo.getMisfireStrategy(), MisfireStrategyEnum.DO_NOTHING);
                                            if (MisfireStrategyEnum.FIRE_ONCE_NOW == misfireStrategyEnum) {
                                                // FIRE_ONCE_NOW ??? trigger ????????????????????????????????????????????????
                                                JobTriggerPoolHelper.trigger(jobInfo.getId(), null, TriggerTypeEnum.MISFIRE, -1, null, null, null);
                                                logger.debug(">>>>>>>>>>> xxl-job, schedule push trigger : jobId = " + jobInfo.getId());
                                            }

                                            // 2???fresh next ???????????????????????????????????????????????????
                                            refreshNextValidTime(jobInfo, new Date());

                                        } else if (nowTime > jobInfo.getTriggerNextTime()) {
                                            // 2.2???trigger-expire < 5s???direct-trigger && make next-trigger-time  ????????????????????????5???

                                            // 1???trigger ??????????????????
                                            JobTriggerPoolHelper.trigger(jobInfo.getId(), null, TriggerTypeEnum.CRON, -1, null, null, null);
                                            logger.debug(">>>>>>>>>>> xxl-job, schedule push trigger : jobId = " + jobInfo.getId());

                                            // 2???fresh next  ???????????????????????????????????????????????????
                                            refreshNextValidTime(jobInfo, new Date());

                                            // next-trigger-time in 5s, pre-read again  ?????????????????????5?????????????????????
                                            if (jobInfo.getTriggerStatus() == 1 && nowTime + PRE_READ_MS > jobInfo.getTriggerNextTime()) {

                                                // 1???make ring second  ???????????????
                                                int ringSecond = (int) ((jobInfo.getTriggerNextTime() / 1000) % 60);

                                                // 2???push time ring  ??????map??????key????????????????????????value????????????????????????jobId
                                                pushTimeRing(ringSecond, jobInfo.getId());

                                                // 3???fresh next ????????????????????????
                                                refreshNextValidTime(jobInfo, new Date(jobInfo.getTriggerNextTime()));

                                            }

                                        } else {
                                            // 2.3???trigger-pre-read???time-ring trigger && make next-trigger-time ??????5??????????????????????????????

                                            // 1???make ring second ???????????????
                                            int ringSecond = (int) ((jobInfo.getTriggerNextTime() / 1000) % 60);

                                            // 2???push time ring ??????map??????key????????????????????????value????????????????????????jobId
                                            pushTimeRing(ringSecond, jobInfo.getId());

                                            // 3???fresh next ??????
                                            refreshNextValidTime(jobInfo, new Date(jobInfo.getTriggerNextTime()));

                                        }
                                    } catch (Exception e) {
                                        logger.error(e.getMessage());
                                    } finally {
                                        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                                        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
                                        redisTemplate.execute(redisScript, Collections.singletonList(JOB_REDIS_LOCK + jobInfo), uuid);
                                    }
                                }

                            }
                            long l = System.currentTimeMillis();
                            // 3???update trigger info ??????????????????
//                            for (XxlJobInfo jobInfo: scheduleList) {
//                                XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().scheduleUpdate(jobInfo);
//                            }
                            XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().scheduleBatchUpdate2(scheduleList);
                            long ll = System.currentTimeMillis();
                            long l1 = ll - l;
                            System.out.println("???????????????" + l1);
                            System.out.println("=====================111===================?????????" + scheduleList.size());
                        }
                    } catch (Exception e) {
                        if (!scheduleThreadToStop) {
                            logger.error(">>>>>>>>>>> xxl-job, JobScheduleHelper#scheduleThread error:{}", e);
                        }
                    }
                    long cost = System.currentTimeMillis() - start;

                    System.out.println("========================================?????????" + cost);
                    // Wait seconds, align second
                    if (cost < 1000) {  // scan-overtime, not wait
                        try {
                            // pre-read period: success > scan each second; fail > skip this period;
                            TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
                        } catch (InterruptedException e) {
                            if (!scheduleThreadToStop) {
                                logger.error(e.getMessage(), e);
                            }
                        }
                    }

                }

                logger.info(">>>>>>>>>>> xxl-job, JobScheduleHelper#scheduleThread stop");
            }
        });
        scheduleThread.setDaemon(true);
        scheduleThread.setName("xxl-job, admin JobScheduleHelper#scheduleThread");
        scheduleThread.start();


        // ring thread
        ringThread = new Thread(new Runnable() {
            @Override
            public void run() {

                while (!ringThreadToStop) {

                    // align second
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
                    } catch (InterruptedException e) {
                        if (!ringThreadToStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }

                    try {
                        // second data
                        List<Integer> ringItemData = new ArrayList<>();
                        int nowSecond = Calendar.getInstance().get(Calendar.SECOND);   // ?????????????????????????????????????????????????????????????????????
                        for (int i = 0; i < 2; i++) {
                            List<Integer> tmpData = ringData.remove((nowSecond + 60 - i) % 60);
                            if (tmpData != null) {
                                ringItemData.addAll(tmpData);
                            }
                        }

                        // ring trigger
                        logger.debug(">>>>>>>>>>> xxl-job, time-ring beat : " + nowSecond + " = " + Arrays.asList(ringItemData));
                        if (ringItemData.size() > 0) {
                            // do trigger
                            for (int jobId : ringItemData) {
                                // do trigger
                                JobTriggerPoolHelper.trigger(jobId, null, TriggerTypeEnum.CRON, -1, null, null, null);
                            }
                            // clear
                            ringItemData.clear();
                        }
                    } catch (Exception e) {
                        if (!ringThreadToStop) {
                            logger.error(">>>>>>>>>>> xxl-job, JobScheduleHelper#ringThread error:{}", e);
                        }
                    }
                }
                logger.info(">>>>>>>>>>> xxl-job, JobScheduleHelper#ringThread stop");
            }
        });
        ringThread.setDaemon(true);
        ringThread.setName("xxl-job, admin JobScheduleHelper#ringThread");
        ringThread.start();
    }

    private void refreshNextValidTime(XxlJobInfo jobInfo, Date fromTime) throws Exception {
        Date nextValidTime = generateNextValidTime(jobInfo, fromTime);
        if (nextValidTime != null) {
            jobInfo.setTriggerLastTime(jobInfo.getTriggerNextTime());
            jobInfo.setTriggerNextTime(nextValidTime.getTime());
        } else {
            jobInfo.setTriggerStatus(0);
            jobInfo.setTriggerLastTime(0);
            jobInfo.setTriggerNextTime(0);
            logger.warn(">>>>>>>>>>> xxl-job, refreshNextValidTime fail for job: jobId={}, scheduleType={}, scheduleConf={}",
                    jobInfo.getId(), jobInfo.getScheduleType(), jobInfo.getScheduleConf());
        }
    }

    private void pushTimeRing(int ringSecond, int jobId) {
        // push async ring
        List<Integer> ringItemData = ringData.get(ringSecond);
        if (ringItemData == null) {
            ringItemData = new ArrayList<Integer>();
            ringData.put(ringSecond, ringItemData);
        }
        ringItemData.add(jobId);

        logger.debug(">>>>>>>>>>> xxl-job, schedule push time-ring : " + ringSecond + " = " + Arrays.asList(ringItemData));
    }

    public void toStop() {

        // 1???stop schedule
        scheduleThreadToStop = true;
        try {
            TimeUnit.SECONDS.sleep(1);  // wait
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
        if (scheduleThread.getState() != Thread.State.TERMINATED) {
            // interrupt and wait
            scheduleThread.interrupt();
            try {
                scheduleThread.join();
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }

        // if has ring data
        boolean hasRingData = false;
        if (!ringData.isEmpty()) {
            for (int second : ringData.keySet()) {
                List<Integer> tmpData = ringData.get(second);
                if (tmpData != null && tmpData.size() > 0) {
                    hasRingData = true;
                    break;
                }
            }
        }
        if (hasRingData) {
            try {
                TimeUnit.SECONDS.sleep(8);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }

        // stop ring (wait job-in-memory stop)
        ringThreadToStop = true;
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
        if (ringThread.getState() != Thread.State.TERMINATED) {
            // interrupt and wait
            ringThread.interrupt();
            try {
                ringThread.join();
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }

        logger.info(">>>>>>>>>>> xxl-job, JobScheduleHelper stop");
    }

}
