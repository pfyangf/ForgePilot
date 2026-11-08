package com.forgepilot.system.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 模型目录的占位入口。骨架不实现维护流程，也不返回任何密钥。
 */
@RestController
@RequestMapping("/api/v1/model-providers")
public class ModelProviderController {

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public void unimplemented() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "模型目录维护尚未实现");
    }
}
