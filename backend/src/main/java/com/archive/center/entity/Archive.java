package com.archive.center.entity;

import jakarta.persistence.*;

/** 案卷：一个卷宗，放在某间库房里。 */
@Entity
@Table(name = "archive")
public class Archive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32, unique = true)
    public String code;

    @Column(nullable = false, length = 128)
    public String title;

    /** 所属年度 */
    @Column(name = "archive_year", nullable = false)
    public Integer archiveYear;

    /** 保管期限，年 */
    @Column(name = "keep_years", nullable = false)
    public Integer keepYears;

    @Column(name = "room_id", nullable = false)
    public Long roomId;

    /** 在库 / 已借出 / 已销毁 */
    @Column(nullable = false, length = 16)
    public String status;
}
