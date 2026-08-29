package com.admin.controller;

import com.admin.common.aop.LogAnnotation;
import com.admin.common.annotation.RequireRole;
import com.admin.common.dto.ForwardDto;
import com.admin.common.dto.ForwardUpdateDto;
import com.admin.common.lang.R;
import com.admin.service.ForwardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @since 2025-06-03
 */
@RestController
@CrossOrigin
@RequestMapping("/api/v1/forward")
public class ForwardController extends BaseController {

    @Autowired
    private ForwardService forwardService;

    @LogAnnotation
    @PostMapping("/create")
    public R create(@Validated @RequestBody ForwardDto forwardDto) {
        return forwardService.createForward(forwardDto);
    }

    @LogAnnotation
    @PostMapping("/batch-create")
    public R batchCreate(@RequestBody Map<String, List<ForwardDto>> params) {
        List<ForwardDto> forwards = params == null ? null : params.get("forwards");
        return forwardService.createForwards(forwards);
    }

    @LogAnnotation
    @PostMapping("/list")
    public R readAll() {
        return forwardService.getAllForwards();
    }

    @LogAnnotation
    @PostMapping("/update")
    public R update(@Validated @RequestBody ForwardUpdateDto forwardUpdateDto) {
        return forwardService.updateForward(forwardUpdateDto);
    }

    @LogAnnotation
    @PostMapping("/delete")
    public R delete(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        return forwardService.deleteForward(id);
    }

    @LogAnnotation
    @PostMapping("/force-delete")
    public R forceDelete(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        return forwardService.forceDeleteForward(id);
    }

    @LogAnnotation
    @PostMapping("/batch-delete")
    @SuppressWarnings("unchecked")
    public R batchDelete(@RequestBody Map<String, Object> params) {
        Object idsObj = params.get("ids");
        if (!(idsObj instanceof List)) {
            return R.err("请提供要删除的转发ID列表");
        }
        List<Object> rawIds = (List<Object>) idsObj;
        List<Long> ids = new ArrayList<>();
        for (Object rawId : rawIds) {
            ids.add(Long.valueOf(rawId.toString()));
        }
        boolean force = Boolean.TRUE.equals(params.get("force"));
        return forwardService.deleteForwards(ids, force);
    }

    @LogAnnotation
    @PostMapping("/pause")
    public R pause(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        return forwardService.pauseForward(id);
    }

    @LogAnnotation
    @PostMapping("/resume")
    public R resume(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        return forwardService.resumeForward(id);
    }

    /**
     * 转发诊断功能
     * @param params 包含forwardId的参数
     * @return 诊断结果
     */
    @LogAnnotation
    @PostMapping("/diagnose")
    public R diagnoseForward(@RequestBody Map<String, Object> params) {
        Long forwardId = Long.valueOf(params.get("forwardId").toString());
        return forwardService.diagnoseForward(forwardId);
    }

    /**
     * 更新转发排序
     * @param params 包含forwards数组的参数，每个元素包含id和inx
     * @return 更新结果
     */
    @LogAnnotation
    @PostMapping("/update-order")
    public R updateForwardOrder(@RequestBody Map<String, Object> params) {
        return forwardService.updateForwardOrder(params);
    }

}
