package com.forgepilot.system.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 操作记录和日报的占位入口。骨架不查询数据，也不返回密钥。 */
@RestController
@RequestMapping("/api/v1")
public class ActivityLogController {

    @GetMapping({"/activity-logs", "/activity-logs/export"})
    public void logs() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "操作记录查询尚未实现");
    }

    @GetMapping({"/reports/daily", "/reports/daily.md"})
    public void daily() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "日报生成尚未实现");
    }
}
