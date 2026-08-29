package com.admin.controller;

import com.admin.common.annotation.RequireRole;
import com.admin.common.aop.LogAnnotation;
import com.admin.common.dto.AggregateForwardDto;
import com.admin.common.lang.R;
import com.admin.service.AggregateForwardService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/aggregate-forward")
public class AggregateForwardController extends BaseController {

    @Resource
    private AggregateForwardService aggregateForwardService;

    @LogAnnotation
    @RequireRole
    @PostMapping("/create")
    public R create(@Validated @RequestBody AggregateForwardDto dto) {
        return aggregateForwardService.createForward(dto);
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/list")
    public R list() {
        return aggregateForwardService.listForwards();
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/update")
    public R update(@Validated @RequestBody AggregateForwardDto dto) {
        return aggregateForwardService.updateForward(dto);
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/delete")
    public R delete(@RequestBody Map<String, Object> params) {
        Long id = parseId(params);
        if (id == null) {
            return R.err("聚合转发ID不能为空");
        }
        return aggregateForwardService.deleteForward(id);
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/pause")
    public R pause(@RequestBody Map<String, Object> params) {
        Long id = parseId(params);
        if (id == null) {
            return R.err("聚合转发ID不能为空");
        }
        return aggregateForwardService.pauseForward(id);
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/resume")
    public R resume(@RequestBody Map<String, Object> params) {
        Long id = parseId(params);
        if (id == null) {
            return R.err("聚合转发ID不能为空");
        }
        return aggregateForwardService.resumeForward(id);
    }

    private Long parseId(Map<String, Object> params) {
        if (params == null || params.get("id") == null) {
            return null;
        }
        try {
            return Long.valueOf(params.get("id").toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
