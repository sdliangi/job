package com.xxl.job.admin.service.impl;

import com.xxl.job.admin.controller.interceptor.UserInterceptor;
import com.xxl.job.admin.core.cron.CronExpression;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLogReport;
import com.xxl.job.admin.core.route.ExecutorRouteStrategyEnum;
import com.xxl.job.admin.core.scheduler.MisfireStrategyEnum;
import com.xxl.job.admin.core.scheduler.ScheduleTypeEnum;
import com.xxl.job.admin.core.thread.JobScheduleHelper;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.core.util.KeyCompareUtil;
import com.xxl.job.admin.core.util.StringUtils;
import com.xxl.job.admin.dao.*;
import com.xxl.job.admin.service.JobTopologyApiService;
import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * core job action for xxl-job
 *
 * @author xuxueli 2016-5-28 15:30:33
 */
@Service
public class XxlJobServiceImpl implements XxlJobService {
	private static Logger logger = LoggerFactory.getLogger(XxlJobServiceImpl.class);
	@Resource
	public XxlJobLogDao xxlJobLogDao;
	@Resource
	private XxlJobGroupDao xxlJobGroupDao;
	@Resource
	private XxlJobInfoDao xxlJobInfoDao;
	@Resource
	private XxlJobLogGlueDao xxlJobLogGlueDao;
	@Resource
	private XxlJobLogReportDao xxlJobLogReportDao;
	@Resource
	private JobTopologyApiService jobTopologyApiService;


	@Override
	public Map<String, Object> pageList(int start, int length, int jobGroup, int triggerStatus, String jobDesc, String executorHandler, String author, int projectId) {

		// page list
		List<XxlJobInfo> list = xxlJobInfoDao.pageList(start, length, jobGroup, triggerStatus, jobDesc, executorHandler, author, projectId);
		int list_count = xxlJobInfoDao.pageListCount(start, length, jobGroup, triggerStatus, jobDesc, executorHandler, author, projectId);

		// package result
		Map<String, Object> maps = new HashMap<String, Object>();
		maps.put("recordsTotal", list_count);        // 总记录数
		maps.put("recordsFiltered", list_count);    // 过滤后的总记录数
		maps.put("data", list);                    // 分页列表
		return maps;
	}

	@Override
	public ReturnT<String> add(XxlJobInfo jobInfo) {

		// valid base
		XxlJobGroup group = xxlJobGroupDao.load(jobInfo.getJobGroup());
		if (group == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("system_please_choose") + I18nUtil.getString("jobinfo_field_jobgroup")));
		}
		if (jobInfo.getJobDesc() == null || jobInfo.getJobDesc().trim().length() == 0) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("system_please_input") + I18nUtil.getString("jobinfo_field_jobdesc")));
		}
		if (jobInfo.getAuthor() == null || jobInfo.getAuthor().trim().length() == 0) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("system_please_input") + I18nUtil.getString("jobinfo_field_author")));
		}
		//TODO 测试环境先注释调
		if (jobInfo.getProjectId() <= 0) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "缺少项目id");
		}


		// valid trigger
		ScheduleTypeEnum scheduleTypeEnum = ScheduleTypeEnum.match(jobInfo.getScheduleType(), null);
		if (scheduleTypeEnum == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
		}
		if (scheduleTypeEnum == ScheduleTypeEnum.CRON) {
			if (jobInfo.getScheduleConf() == null || !CronExpression.isValidExpression(jobInfo.getScheduleConf())) {
				return new ReturnT<String>(ReturnT.FAIL_CODE, "Cron" + I18nUtil.getString("system_unvalid"));
			}
		} else if (scheduleTypeEnum == ScheduleTypeEnum.FIX_RATE/* || scheduleTypeEnum == ScheduleTypeEnum.FIX_DELAY*/) {
			if (jobInfo.getScheduleConf() == null) {
				return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type")));
			}
			try {
				int fixSecond = Integer.valueOf(jobInfo.getScheduleConf());
				if (fixSecond < 1) {
					return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
				}
			} catch (Exception e) {
				return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
			}
		} else if (scheduleTypeEnum == ScheduleTypeEnum.NONE) {
			//调度类型为NONE，任务只可以手动调度，或者当做子任务。不可以作为主任务
			jobInfo.setIsMasterJob(0);
		}

		// valid job
		if (GlueTypeEnum.match(jobInfo.getGlueType()) == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("jobinfo_field_gluetype") + I18nUtil.getString("system_unvalid")));
		}
		if (GlueTypeEnum.BEAN == GlueTypeEnum.match(jobInfo.getGlueType()) && (jobInfo.getExecutorHandler() == null || jobInfo.getExecutorHandler().trim().length() == 0)) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("system_please_input") + "JobHandler"));
		}
		// 》fix "\r" in shell
		if (GlueTypeEnum.GLUE_SHELL == GlueTypeEnum.match(jobInfo.getGlueType()) && jobInfo.getGlueSource() != null) {
			jobInfo.setGlueSource(jobInfo.getGlueSource().replaceAll("\r", ""));
		}

		// valid advanced
		if (ExecutorRouteStrategyEnum.match(jobInfo.getExecutorRouteStrategy(), null) == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("jobinfo_field_executorRouteStrategy") + I18nUtil.getString("system_unvalid")));
		}
		if (MisfireStrategyEnum.match(jobInfo.getMisfireStrategy(), null) == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("misfire_strategy") + I18nUtil.getString("system_unvalid")));
		}
		if (ExecutorBlockStrategyEnum.match(jobInfo.getExecutorBlockStrategy(), null) == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("jobinfo_field_executorBlockStrategy") + I18nUtil.getString("system_unvalid")));
		}

		// 》ChildJobId valid
		if (jobInfo.getChildJobId() != null && jobInfo.getChildJobId().trim().length() > 0) {
			String[] childJobIds = jobInfo.getChildJobId().split(",");
			for (String childJobIdItem : childJobIds) {
				if (childJobIdItem != null && childJobIdItem.trim().length() > 0 && isNumeric(childJobIdItem)) {
					XxlJobInfo childJobInfo = xxlJobInfoDao.loadById(Integer.parseInt(childJobIdItem));
					if (childJobInfo == null) {
						return new ReturnT<String>(ReturnT.FAIL_CODE,
								MessageFormat.format((I18nUtil.getString("jobinfo_field_childJobId") + "({0})" + I18nUtil.getString("system_not_found")), childJobIdItem));
					}
					// 判断是否属于同一项目
					if (childJobInfo.getProjectId() != jobInfo.getProjectId()) {
						return new ReturnT<>(ReturnT.FAIL_CODE, "子任务非法请检查");
					}
					//判断此次添加的子任务链中的任务是否合法
					ReturnT returnT = validChildJob(Integer.parseInt(childJobIdItem), jobInfo.getId());
					if (returnT.getCode() != 200) {
						return returnT;
					}


				} else {
					return new ReturnT<String>(ReturnT.FAIL_CODE,
							MessageFormat.format((I18nUtil.getString("jobinfo_field_childJobId") + "({0})" + I18nUtil.getString("system_unvalid")), childJobIdItem));
				}
			}

			// join , avoid "xxx,,"
			String temp = "";
			for (String item : childJobIds) {
				temp += item + ",";
			}
			temp = temp.substring(0, temp.length() - 1);

			jobInfo.setChildJobId(temp);
		}

		// add in db
		jobInfo.setAddTime(new Date());
		jobInfo.setUpdateTime(new Date());
		jobInfo.setGlueUpdatetime(new Date());
		xxlJobInfoDao.save(jobInfo);
		if (jobInfo.getId() < 1) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("jobinfo_field_add") + I18nUtil.getString("system_fail")));
		}

		return new ReturnT<String>(String.valueOf(jobInfo.getId()));
	}


	public ReturnT validChildJob(int childId, int jobId) {
		if (childId == jobId) {
			return new ReturnT(ReturnT.FAIL_CODE, "子任务不可以是自身ID");
		}
		XxlJobInfo childJob = xxlJobInfoDao.loadById(childId);
		if (childJob.getIsMasterJob() == 1) {
			return new ReturnT(ReturnT.FAIL_CODE, "不可将主任务节点设置成子任务");
		}
		List<XxlJobInfo> preJobs = xxlJobInfoDao.getPreJobByChildId(childId);
		if (preJobs.size() > 0) {
			//判断是否属于自己拥有过的
			List<Integer> preIds = preJobs.stream().map(item -> item.getId()).collect(Collectors.toList());
			if (!preIds.contains(jobId)) {
				return new ReturnT(ReturnT.FAIL_CODE, "该子任务已经被使用，请重新创建");
			}
		}

		String childJobIds = xxlJobInfoDao.getChildJobIdById(childId);
		if (!StringUtils.isEmpty(childJobIds)) {
			for (String id : childJobIds.split(",")) {
				if (isUsed(id)) {
					return new ReturnT(ReturnT.FAIL_CODE, "该子任务的子任务已被其他任务链使用，请重新创建");
				}
			}
		}

		return ReturnT.SUCCESS;

	}

	public boolean isUsed(String id) {
		List<XxlJobInfo> preJobs = xxlJobInfoDao.getPreJobByChildId(Integer.parseInt(id));
		if (preJobs.size() > 1) {
			return true;
		}
		String childJobIds = xxlJobInfoDao.getChildJobIdById(Integer.parseInt(id));
		if (!StringUtils.isEmpty(childJobIds)) {
			for (String childId : childJobIds.split(",")) {
				return isUsed(childId);
			}
		}
		return false;
	}


	private boolean isNumeric(String str) {
		try {
			int result = Integer.valueOf(str);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	@Override
	public ReturnT<String> update(XxlJobInfo jobInfo) {

		// valid base
		if (jobInfo.getJobDesc() == null || jobInfo.getJobDesc().trim().length() == 0) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("system_please_input") + I18nUtil.getString("jobinfo_field_jobdesc")));
		}
		if (jobInfo.getAuthor() == null || jobInfo.getAuthor().trim().length() == 0) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("system_please_input") + I18nUtil.getString("jobinfo_field_author")));
		}
		// TODO 测试环境注释掉
		if (jobInfo.getProjectId() <= 0) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "缺少项目id");
		}

		// valid trigger
		ScheduleTypeEnum scheduleTypeEnum = ScheduleTypeEnum.match(jobInfo.getScheduleType(), null);
		if (scheduleTypeEnum == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
		}
		if (scheduleTypeEnum == ScheduleTypeEnum.CRON) {
			if (jobInfo.getScheduleConf() == null || !CronExpression.isValidExpression(jobInfo.getScheduleConf())) {
				return new ReturnT<String>(ReturnT.FAIL_CODE, "Cron" + I18nUtil.getString("system_unvalid"));
			}
		} else if (scheduleTypeEnum == ScheduleTypeEnum.FIX_RATE /*|| scheduleTypeEnum == ScheduleTypeEnum.FIX_DELAY*/) {
			if (jobInfo.getScheduleConf() == null) {
				return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
			}
			try {
				int fixSecond = Integer.valueOf(jobInfo.getScheduleConf());
				if (fixSecond < 1) {
					return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
				}
			} catch (Exception e) {
				return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
			}
		} else if (scheduleTypeEnum == ScheduleTypeEnum.NONE) {
			//调度类型为NONE，任务只可以手动调度，或者当做子任务。不可以作为主任务
			jobInfo.setIsMasterJob(0);
		}

		// valid advanced
		if (ExecutorRouteStrategyEnum.match(jobInfo.getExecutorRouteStrategy(), null) == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("jobinfo_field_executorRouteStrategy") + I18nUtil.getString("system_unvalid")));
		}
		if (MisfireStrategyEnum.match(jobInfo.getMisfireStrategy(), null) == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("misfire_strategy") + I18nUtil.getString("system_unvalid")));
		}
		if (ExecutorBlockStrategyEnum.match(jobInfo.getExecutorBlockStrategy(), null) == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("jobinfo_field_executorBlockStrategy") + I18nUtil.getString("system_unvalid")));
		}

		// 》ChildJobId valid
		if (jobInfo.getChildJobId() != null && jobInfo.getChildJobId().trim().length() > 0) {
			String[] childJobIds = jobInfo.getChildJobId().split(",");
			for (String childJobIdItem : childJobIds) {
				if (childJobIdItem != null && childJobIdItem.trim().length() > 0 && isNumeric(childJobIdItem)) {
					XxlJobInfo childJobInfo = xxlJobInfoDao.loadById(Integer.parseInt(childJobIdItem));
					if (childJobInfo == null) {
						return new ReturnT<String>(ReturnT.FAIL_CODE,
								MessageFormat.format((I18nUtil.getString("jobinfo_field_childJobId") + "({0})" + I18nUtil.getString("system_not_found")), childJobIdItem));
					}
					// 判断是否属于同一项目
					if (childJobInfo.getProjectId() != jobInfo.getProjectId()) {
						return new ReturnT<>(ReturnT.FAIL_CODE, "子任务非法请检查");
					}
					//判断此次添加的子任务链中的任务是否合法
					ReturnT returnT = validChildJob(Integer.parseInt(childJobIdItem), jobInfo.getId());
					if (returnT.getCode() != 200) {
						return returnT;
					}

				} else {
					return new ReturnT<String>(ReturnT.FAIL_CODE,
							MessageFormat.format((I18nUtil.getString("jobinfo_field_childJobId") + "({0})" + I18nUtil.getString("system_unvalid")), childJobIdItem));
				}
			}

			// join , avoid "xxx,,"
			String temp = "";
			for (String item : childJobIds) {
				temp += item + ",";
			}
			temp = temp.substring(0, temp.length() - 1);

			jobInfo.setChildJobId(temp);
		}

		// group valid
		XxlJobGroup jobGroup = xxlJobGroupDao.load(jobInfo.getJobGroup());
		if (jobGroup == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("jobinfo_field_jobgroup") + I18nUtil.getString("system_unvalid")));
		}

		// stage job info
		XxlJobInfo exists_jobInfo = xxlJobInfoDao.loadById(jobInfo.getId());
		if (exists_jobInfo == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("jobinfo_field_id") + I18nUtil.getString("system_not_found")));
		}

		// next trigger time (5s后生效，避开预读周期)
		long nextTriggerTime = exists_jobInfo.getTriggerNextTime();
		boolean scheduleDataNotChanged = jobInfo.getScheduleType().equals(exists_jobInfo.getScheduleType()) && jobInfo.getScheduleConf().equals(exists_jobInfo.getScheduleConf());
		if (exists_jobInfo.getTriggerStatus() == 1 && !scheduleDataNotChanged) {
			try {
				Date nextValidTime = JobScheduleHelper.generateNextValidTime(jobInfo, new Date(System.currentTimeMillis() + JobScheduleHelper.PRE_READ_MS));
				if (nextValidTime == null) {
					return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
				}
				nextTriggerTime = nextValidTime.getTime();
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
				return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
			}
		}

		exists_jobInfo.setJobGroup(jobInfo.getJobGroup());
		exists_jobInfo.setProjectId(jobInfo.getProjectId());
		exists_jobInfo.setJobDesc(jobInfo.getJobDesc());
		exists_jobInfo.setAuthor(jobInfo.getAuthor());
		exists_jobInfo.setAlarmEmail(jobInfo.getAlarmEmail());
		exists_jobInfo.setAlarmMq(jobInfo.getAlarmMq());
		exists_jobInfo.setAlarmWebhook(jobInfo.getAlarmWebhook());
		exists_jobInfo.setScheduleType(jobInfo.getScheduleType());
		exists_jobInfo.setScheduleConf(jobInfo.getScheduleConf());
		exists_jobInfo.setMisfireStrategy(jobInfo.getMisfireStrategy());
		exists_jobInfo.setExecutorRouteStrategy(jobInfo.getExecutorRouteStrategy());
		exists_jobInfo.setExecutorHandler(jobInfo.getExecutorHandler());
		exists_jobInfo.setExecutorParam(jobInfo.getExecutorParam());
		exists_jobInfo.setExecutorBlockStrategy(jobInfo.getExecutorBlockStrategy());
		exists_jobInfo.setExecutorTimeout(jobInfo.getExecutorTimeout());
		exists_jobInfo.setExecutorFailRetryCount(jobInfo.getExecutorFailRetryCount());
		exists_jobInfo.setChildJobId(jobInfo.getChildJobId());
		exists_jobInfo.setTriggerNextTime(nextTriggerTime);

		exists_jobInfo.setUpdateTime(new Date());
		xxlJobInfoDao.update(exists_jobInfo);


		return ReturnT.SUCCESS;
	}

	@Transactional
	@Override
	public ReturnT<String> remove(int id, int projectId) {
		XxlJobInfo xxlJobInfo = xxlJobInfoDao.loadById(id);
		if (xxlJobInfo == null) {
			return ReturnT.SUCCESS;
		}

		if (projectId > 0 && xxlJobInfo.getProjectId() != projectId) {
			return ReturnT.FAIL;
		}

		xxlJobInfoDao.delete(id);
		xxlJobLogDao.delete(id);
		xxlJobLogGlueDao.deleteByJobId(id);
		//清除父任务的引用
		List<XxlJobInfo> preJobs = xxlJobInfoDao.getPreJobByChildId(id);
		for (XxlJobInfo preJob : preJobs) {
//			String childJobId = preJob.getChildJobId();
			ReturnT disconnect = jobTopologyApiService.disconnect(preJob.getId(), id);
			if (disconnect.getCode() != 200) {
				return ReturnT.FAIL;
			}
			/*if (isNumeric(childJobId)){
				xxlJobInfoDao.updateChildIdById(null,preJob.getId());
			}else if (childJobId.contains(id+",")){
				childJobId=childJobId.replace(id+",","");
				xxlJobInfoDao.updateChildIdById(childJobId,preJob.getId());
			}else if (childJobId.contains(","+id)){
				childJobId=childJobId.replace(","+id,"");
				xxlJobInfoDao.updateChildIdById(childJobId,preJob.getId());
			}else {
				logger.warn("childJobId：{}不合法",childJobId);
				return ReturnT.FAIL;
			}*/
		}
		return ReturnT.SUCCESS;
	}

	@Override
	public ReturnT<String> start(int id) {
		XxlJobInfo xxlJobInfo = xxlJobInfoDao.loadById(id);

		// valid
		ScheduleTypeEnum scheduleTypeEnum = ScheduleTypeEnum.match(xxlJobInfo.getScheduleType(), ScheduleTypeEnum.NONE);
		if (ScheduleTypeEnum.NONE == scheduleTypeEnum) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type_none_limit_start")));
		}

		if (Integer.parseInt(UserInterceptor.projectThread.get()) > 0 && xxlJobInfo.getProjectId() != Integer.parseInt(UserInterceptor.projectThread.get())) {
			return ReturnT.FAIL;
		}

		// next trigger time (5s后生效，避开预读周期)
		long nextTriggerTime = 0;
		try {
			Date nextValidTime = JobScheduleHelper.generateNextValidTime(xxlJobInfo, new Date(System.currentTimeMillis() + JobScheduleHelper.PRE_READ_MS));
			if (nextValidTime == null) {
				return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
			}
			nextTriggerTime = nextValidTime.getTime();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return new ReturnT<String>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")));
		}

		xxlJobInfo.setTriggerStatus(1);
		xxlJobInfo.setTriggerLastTime(0);
		xxlJobInfo.setTriggerNextTime(nextTriggerTime);

		xxlJobInfo.setUpdateTime(new Date());
		xxlJobInfoDao.update(xxlJobInfo);
		return ReturnT.SUCCESS;
	}

	@Override
	public ReturnT<String> stop(int id) {
		XxlJobInfo xxlJobInfo = xxlJobInfoDao.loadById(id);

		if (Integer.parseInt(UserInterceptor.projectThread.get()) > 0 && xxlJobInfo.getProjectId() != Integer.parseInt(UserInterceptor.projectThread.get())) {
			return ReturnT.FAIL;
		}
		xxlJobInfo.setTriggerStatus(0);
		xxlJobInfo.setTriggerLastTime(0);
		xxlJobInfo.setTriggerNextTime(0);

		xxlJobInfo.setUpdateTime(new Date());
		xxlJobInfoDao.update(xxlJobInfo);
		return ReturnT.SUCCESS;
	}

	@Override
	public Map<String, Object> dashboardInfo(int projectId) {

		int jobInfoCount = xxlJobInfoDao.findRunningCount(projectId);
		int jobLogCount = 0;
		int jobLogSuccessCount = 0;

		XxlJobLogReport xxlJobLogReport = xxlJobLogReportDao.queryLogReportTotal(projectId);
		if (xxlJobLogReport != null) {
			jobLogCount = xxlJobLogReport.getRunningCount() + xxlJobLogReport.getSucCount() + xxlJobLogReport.getFailCount();
			jobLogSuccessCount = xxlJobLogReport.getSucCount();
		}

		// executor count
		Set<String> executorAddressSet = new HashSet<String>();
		List<XxlJobGroup> groupList = xxlJobGroupDao.findAll();

		if (groupList != null && !groupList.isEmpty()) {
			for (XxlJobGroup group : groupList) {
				if (group.getRegistryList() != null && !group.getRegistryList().isEmpty()) {
					executorAddressSet.addAll(group.getRegistryList());
				}
			}
		}

		int executorCount = executorAddressSet.size();

		Map<String, Object> dashboardMap = new HashMap<String, Object>();
		dashboardMap.put("jobInfoCount", jobInfoCount);
		dashboardMap.put("jobLogCount", jobLogCount);
		dashboardMap.put("jobLogSuccessCount", jobLogSuccessCount);
		dashboardMap.put("executorCount", executorCount);
		return dashboardMap;
	}

	@Override
	public ReturnT<Map<String, Object>> chartInfo(Date startDate, Date endDate, int projectId) {

		// process
		List<String> triggerDayList = new ArrayList<String>();
		List<Integer> triggerDayCountRunningList = new ArrayList<Integer>();
		List<Integer> triggerDayCountSucList = new ArrayList<Integer>();
		List<Integer> triggerDayCountFailList = new ArrayList<Integer>();
		int triggerCountRunningTotal = 0;
		int triggerCountSucTotal = 0;
		int triggerCountFailTotal = 0;

		List<XxlJobLogReport> logReportList = xxlJobLogReportDao.queryLogReport(startDate, endDate, projectId);

		if (logReportList != null && logReportList.size() > 0) {
			if (projectId == 0) {//系统级别查所有
				Map<Date, List<XxlJobLogReport>> collect = logReportList.stream().collect(Collectors.groupingBy(XxlJobLogReport::getTriggerDay));
				//按日期排序
				Map<Date, List<XxlJobLogReport>> sortMap = new TreeMap<>(new KeyCompareUtil());
				sortMap.putAll(collect);
				for (Map.Entry<Date, List<XxlJobLogReport>> entry : sortMap.entrySet()) {
					triggerDayList.add(DateUtil.formatDate(entry.getKey()));
					List<XxlJobLogReport> value = entry.getValue();

					//每天的总数
					int triggerCountRunningDayTotal = 0;
					int triggerCountSucDayTotal = 0;
					int triggerCountFailDayTotal = 0;

					for (XxlJobLogReport item : value) {
						int triggerDayCountRunning = item.getRunningCount();
						int triggerDayCountSuc = item.getSucCount();
						int triggerDayCountFail = item.getFailCount();

						triggerCountRunningTotal += triggerDayCountRunning;
						triggerCountSucTotal += triggerDayCountSuc;
						triggerCountFailTotal += triggerDayCountFail;

						triggerCountRunningDayTotal += triggerDayCountRunning;
						triggerCountSucDayTotal += triggerDayCountSuc;
						triggerCountFailDayTotal += triggerDayCountFail;
					}
					triggerDayCountRunningList.add(triggerCountRunningDayTotal);
					triggerDayCountSucList.add(triggerCountSucDayTotal);
					triggerDayCountFailList.add(triggerCountFailDayTotal);
				}
			} else {
				for (XxlJobLogReport item : logReportList) {
					String day = DateUtil.formatDate(item.getTriggerDay());
					int triggerDayCountRunning = item.getRunningCount();
					int triggerDayCountSuc = item.getSucCount();
					int triggerDayCountFail = item.getFailCount();

					triggerDayList.add(day);
					triggerDayCountRunningList.add(triggerDayCountRunning);
					triggerDayCountSucList.add(triggerDayCountSuc);
					triggerDayCountFailList.add(triggerDayCountFail);

					triggerCountRunningTotal += triggerDayCountRunning;
					triggerCountSucTotal += triggerDayCountSuc;
					triggerCountFailTotal += triggerDayCountFail;
				}
			}


		} else {
			for (int i = -6; i <= 0; i++) {
				triggerDayList.add(DateUtil.formatDate(DateUtil.addDays(new Date(), i)));
				triggerDayCountRunningList.add(0);
				triggerDayCountSucList.add(0);
				triggerDayCountFailList.add(0);
			}
		}


		Map<String, Object> result = new HashMap<String, Object>();
		result.put("triggerDayList", triggerDayList);
		result.put("triggerDayCountRunningList", triggerDayCountRunningList);
		result.put("triggerDayCountSucList", triggerDayCountSucList);
		result.put("triggerDayCountFailList", triggerDayCountFailList);

		result.put("triggerCountRunningTotal", triggerCountRunningTotal);
		result.put("triggerCountSucTotal", triggerCountSucTotal);
		result.put("triggerCountFailTotal", triggerCountFailTotal);

		return new ReturnT<Map<String, Object>>(result);
	}

}
