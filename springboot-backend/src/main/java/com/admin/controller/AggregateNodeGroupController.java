package com.admin.controller;

import com.admin.common.annotation.RequireRole;
import com.admin.common.aop.LogAnnotation;
import com.admin.common.dto.AggregateNodeGroupDto;
import com.admin.common.lang.R;
import com.admin.service.AggregateNodeGroupService;
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
@RequestMapping("/api/v1/aggregate-node-group")
public class AggregateNodeGroupController extends BaseController {

    @Resource
    private AggregateNodeGroupService aggregateNodeGroupService;

    @LogAnnotation
    @RequireRole
    @PostMapping("/create")
    public R create(@Validated @RequestBody AggregateNodeGroupDto dto) {
        return aggregateNodeGroupService.createGroup(dto);
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/list")
    public R list() {
        return aggregateNodeGroupService.listGroups();
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/update")
    public R update(@Validated @RequestBody AggregateNodeGroupDto dto) {
        return aggregateNodeGroupService.updateGroup(dto);
    }

    @LogAnnotation
    @RequireRole
    @PostMapping("/delete")
    public R delete(@RequestBody Map<String, Object> params) {
        Long id = parseId(params);
        if (id == null) {
            return R.err("节点组ID不能为空");
        }
        return aggregateNodeGroupService.deleteGroup(id);
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
