package com.archive.center.controller;

import com.archive.center.dto.AppraisalPendingRow;
import com.archive.center.dto.AppraisalSubmit;
import com.archive.center.entity.Appraisal;
import com.archive.center.service.AppraisalService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appraisals")
public class AppraisalController {

    private final AppraisalService service;

    public AppraisalController(AppraisalService service) {
        this.service = service;
    }

    /** 到期鉴定台：到期未销毁卷的待鉴定名单。 */
    @GetMapping("/pending")
    public List<AppraisalPendingRow> pending() {
        return service.pending();
    }

    /** 鉴定单列表（未结案 / 已续存 / 已销毁），刷新后对得上。 */
    @GetMapping
    public List<Appraisal> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    /** 给一卷到期卷开鉴定单。 */
    @PostMapping
    public Appraisal open(@RequestParam Long archiveId) {
        return service.open(archiveId);
    }

    /** 会签提交：续存或销毁。 */
    @PostMapping("/{id}/submit")
    public Appraisal submit(@PathVariable Long id, @RequestBody AppraisalSubmit input) {
        return service.submit(id, input);
    }
}
