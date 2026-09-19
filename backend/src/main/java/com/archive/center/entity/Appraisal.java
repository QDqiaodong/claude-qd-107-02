package com.archive.center.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * 到期鉴定单：一卷到期卷先过鉴定会签，会签齐了才允许销毁或续存。
 * 一张卷同一时间只能有一条未结案例（由库内唯一索引和开单行锁共同保证）。
 */
@Entity
@Table(name = "appraisal")
public class Appraisal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "archive_id", nullable = false)
    public Long archiveId;

    /** 鉴定意见：销毁 / 续存（会签提交时才落字，开单时为空） */
    @Column(length = 8)
    public String opinion;

    /** 鉴定人 */
    @Column(length = 32)
    public String appraiser;

    /** 分管领导（会签两人，名字不得相同） */
    @Column(length = 32)
    public String leader;

    /** 续存年数，结论为续存时必填；销毁时为空 */
    @Column(name = "extend_years")
    public Integer extendYears;

    /** 未结案 / 已销毁 / 已续存 */
    @Column(nullable = false, length = 8)
    public String status;

    @Column(name = "created_date", nullable = false)
    public LocalDate createdDate;

    @Column(name = "closed_date")
    public LocalDate closedDate;

    /** 开单时的目录侧快照：提交时用来发现会签期间有没有人擅自动过目录 */
    @Column(name = "snapshot_keep_years", nullable = false)
    public Integer snapshotKeepYears;

    @Column(name = "snapshot_status", nullable = false, length = 16)
    public String snapshotStatus;
}
