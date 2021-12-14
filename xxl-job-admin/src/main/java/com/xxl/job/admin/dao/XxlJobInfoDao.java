package com.xxl.job.admin.dao;

import com.xxl.job.admin.core.model.XxlJobInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


/**
 * job info
 *
 * @author xuxueli 2016-1-12 18:03:45
 */
@Mapper
public interface XxlJobInfoDao {

    public List<XxlJobInfo> pageList(@Param("offset") int offset,
                                     @Param("pagesize") int pagesize,
                                     @Param("jobGroup") int jobGroup,
                                     @Param("triggerStatus") int triggerStatus,
                                     @Param("jobDesc") String jobDesc,
                                     @Param("executorHandler") String executorHandler,
                                     @Param("author") String author,
                                     @Param("projectId") int projectId);

    public int pageListCount(@Param("offset") int offset,
                             @Param("pagesize") int pagesize,
                             @Param("jobGroup") int jobGroup,
                             @Param("triggerStatus") int triggerStatus,
                             @Param("jobDesc") String jobDesc,
                             @Param("executorHandler") String executorHandler,
                             @Param("author") String author,
                             @Param("projectId") int projectId);

    public int save(XxlJobInfo info);

    public int saveBatch(@Param("list") List<XxlJobInfo> list);

    public XxlJobInfo loadById(@Param("id") int id);

    public int update(XxlJobInfo xxlJobInfo);

    public int delete(@Param("id") long id);

    public List<XxlJobInfo> getJobsByGroup(@Param("jobGroup") int jobGroup);

    public int findAllCount(@Param("projectId") int projectId);

    public int findRunningCount(@Param("projectId") int projectId);

    public List<XxlJobInfo> scheduleJobQuery(@Param("maxNextTime") long maxNextTime, @Param("pagesize") int pagesize);

    public int scheduleUpdate(XxlJobInfo xxlJobInfo);

    public int scheduleBatchUpdate(@Param("xxlJobInfos") List<XxlJobInfo> xxlJobInfos);

    public int scheduleBatchUpdate2(@Param("list") List<XxlJobInfo> list);


    String getChildJobIdById(@Param("id") int id);

    List<XxlJobInfo> getPreJobByChildId(@Param("jobId") int jobId);

    void updateChildIdById(@Param("childIds") String childIds, @Param("id") int id);

    List<XxlJobInfo> pageMasterJobList(@Param("offset") int start, @Param("pagesize") int length, @Param("jobGroup") int jobGroup, @Param("triggerStatus") int triggerStatus, @Param("jobDesc") String jobDesc, @Param("executorHandler") String executorHandler, @Param("author") String author);

    List<XxlJobInfo> queryRunningJobByScheduleType(@Param("projectId") int projectId);

    List<XxlJobInfo> getJobsByGroupAndPid(@Param("jobGroup") int jobGroup, @Param("projectId") int projectId);
}
