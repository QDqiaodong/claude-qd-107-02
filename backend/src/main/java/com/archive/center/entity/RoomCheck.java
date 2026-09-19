package com.archive.center.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 库房温湿度检查：每间库房每天量一次。 */
@Entity
@Table(name = "room_check")
public class RoomCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "room_id", nullable = false)
    public Long roomId;

    @Column(name = "check_date", nullable = false)
    public LocalDate checkDate;

    /** 温度，摄氏度 */
    @Column(nullable = false)
    public Double temperature;

    /** 相对湿度百分比 */
    @Column(nullable = false)
    public Double humidity;

    /** 正常 / 异常 */
    @Column(nullable = false, length = 16)
    public String result;

    @Column(name = "issue_desc", length = 255)
    public String issueDesc;

    @Column(nullable = false, length = 32)
    public String checker;
}
