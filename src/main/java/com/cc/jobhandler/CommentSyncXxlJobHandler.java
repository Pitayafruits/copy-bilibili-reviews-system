package com.cc.jobhandler;

import com.cc.service.CommentSyncService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommentSyncXxlJobHandler {

    private static final Logger log = LoggerFactory.getLogger(CommentSyncXxlJobHandler.class);

    @Autowired
    private CommentSyncService commentSyncService;


    @XxlJob("syncHotCommentsJobHandler")
    public void executeSyncHotComments() {
        XxlJobHelper.log("【热点评论同步任务】开始执行...");
        log.info("【热点评论同步任务】XXL-Job 触发，开始执行...");

        try {
            // 调用核心业务逻辑
            commentSyncService.syncHotCommentsToRedis();

            XxlJobHelper.log("【热点评论同步任务】执行成功！");
            log.info("【热点评论同步任务】执行成功！");

            // 主动上报执行成功
            XxlJobHelper.handleSuccess();
        } catch (Exception e) {
            XxlJobHelper.log("【热点评论同步任务】执行失败！错误信息: {}", e.getMessage());
            log.error("【热点评论同步任务】执行失败！", e);

            // 主动上报执行失败
            XxlJobHelper.handleFail(e.getMessage());
        }
    }
}
