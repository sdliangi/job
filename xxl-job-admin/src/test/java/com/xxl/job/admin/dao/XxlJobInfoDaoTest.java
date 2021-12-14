package com.xxl.job.admin.dao;

import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.scheduler.MisfireStrategyEnum;
import com.xxl.job.admin.core.scheduler.ScheduleTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class XxlJobInfoDaoTest {


    @Autowired
    RestTemplate restTemplate;
    @Resource
    private XxlJobInfoDao xxlJobInfoDao;

    @Test
    public void pageList() {
//		List<XxlJobInfo> list = xxlJobInfoDao.pageList(0, 20, 0, -1, null, null, null);
//		int list_count = xxlJobInfoDao.pageListCount(0, 20, 0, -1, null, null, null);

//		System.out.println(list);
//		System.out.println(list_count);

//		List<XxlJobInfo> list2 = xxlJobInfoDao.getJobsByGroup(1);

        Map entity = restTemplate.getForObject("http://192.168.1.66:18080/auth-v3/project/all/noPage", Map.class);
        System.out.println(entity.get("projects"));
    }

    @Test
    public void save_load() {
        XxlJobInfo info = new XxlJobInfo();
        info.setJobGroup(1);
        info.setJobDesc("desc");
        info.setAuthor("setAuthor");
        info.setAlarmEmail("setAlarmEmail");
        info.setScheduleType(ScheduleTypeEnum.FIX_RATE.name());
        info.setScheduleConf(String.valueOf(33));
        info.setMisfireStrategy(MisfireStrategyEnum.DO_NOTHING.name());
        info.setExecutorRouteStrategy("setExecutorRouteStrategy");
        info.setExecutorHandler("setExecutorHandler");
        info.setExecutorParam("setExecutorParam");
        info.setExecutorBlockStrategy("setExecutorBlockStrategy");
        info.setGlueType("setGlueType");
        info.setGlueSource("setGlueSource");
        info.setGlueRemark("setGlueRemark");
        info.setChildJobId("1");

        info.setAddTime(new Date());
        info.setUpdateTime(new Date());
        info.setGlueUpdatetime(new Date());

        int count = xxlJobInfoDao.save(info);

        XxlJobInfo info2 = xxlJobInfoDao.loadById(info.getId());
        info.setScheduleType(ScheduleTypeEnum.FIX_RATE.name());
        info.setScheduleConf(String.valueOf(44));
        info.setMisfireStrategy(MisfireStrategyEnum.FIRE_ONCE_NOW.name());
        info2.setJobDesc("desc2");
        info2.setAuthor("setAuthor2");
        info2.setAlarmEmail("setAlarmEmail2");
        info2.setExecutorRouteStrategy("setExecutorRouteStrategy2");
        info2.setExecutorHandler("setExecutorHandler2");
        info2.setExecutorParam("setExecutorParam2");
        info2.setExecutorBlockStrategy("setExecutorBlockStrategy2");
        info2.setGlueType("setGlueType2");
        info2.setGlueSource("setGlueSource2");
        info2.setGlueRemark("setGlueRemark2");
        info2.setGlueUpdatetime(new Date());
        info2.setChildJobId("1");

        info2.setUpdateTime(new Date());
        int item2 = xxlJobInfoDao.update(info2);

        xxlJobInfoDao.delete(info2.getId());

        List<XxlJobInfo> list2 = xxlJobInfoDao.getJobsByGroup(1);

        int ret3 = xxlJobInfoDao.findAllCount(0);

    }

    @Test
    public void add() {
        List<XxlJobInfo> list = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            XxlJobInfo info = new XxlJobInfo();
            info.setJobGroup(1);
            info.setProjectId(1);
            info.setJobDesc("desc-test" + i);
            info.setAuthor("setAuthor");
            info.setAlarmEmail("shideliang@deri.energy");
            info.setScheduleType(ScheduleTypeEnum.CRON.name());
            info.setScheduleConf("" + i % 60 + " 0/1 * * * ?");
            info.setMisfireStrategy(MisfireStrategyEnum.DO_NOTHING.name());
            info.setExecutorRouteStrategy("RANDOM");
            info.setExecutorHandler("testTask");
            info.setExecutorParam("123");
            info.setExecutorBlockStrategy("SERIAL_EXECUTION");
            info.setGlueType("BEAN");

            info.setAddTime(new Date());
            info.setUpdateTime(new Date());
            info.setGlueUpdatetime(new Date());
            info.setTriggerStatus(0);
            info.setIsMasterJob(1);
            list.add(info);
        }
        xxlJobInfoDao.saveBatch(list);

    }

}
