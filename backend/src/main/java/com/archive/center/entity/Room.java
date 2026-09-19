package com.archive.center.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;

/** 档案库房：一间能放卷宗的屋子。 */
@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32, unique = true)
    public String code;

    @Column(nullable = false, length = 64)
    public String name;

    /** 能放多少卷 */
    @Column(nullable = false)
    public Integer capacity;

    /** 在用 / 停用 / 整理 */
    @Column(nullable = false, length = 16)
    public String status;

    /** 本库温度下限（℃），空则按默认区间判 */
    @Column(name = "temp_min")
    public Double tempMin;

    /** 本库温度上限（℃） */
    @Column(name = "temp_max")
    public Double tempMax;

    /** 本库湿度下限（%） */
    @Column(name = "humidity_min")
    public Double humidityMin;

    /** 本库湿度上限（%） */
    @Column(name = "humidity_max")
    public Double humidityMax;

    /**
     * 封库中：连续两班最新抄表都越本库上下限。
     * 只能由抄表驱动（下一班回到区间内才回温），库房/目录接口都改不动它。
     */
    @Column(nullable = false)
    public Boolean sealed = false;

    /** 封库起始日期（连续第二班越限的抄表日期） */
    @Column(name = "sealed_date")
    public LocalDate sealedDate;

    /**
     * 在册数：还占着这间库房位置的卷（在库 + 已借出，不含已销毁）。
     * 不入库存库，只由服务端在返回前按 archive 表实时算，刷新即对账。
     */
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Integer registered;

    /** 还能放几卷：容量 - 在册数；已经超容时为 0，绝不会给出负数。 */
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Integer availableSlots;

    /** 是否已经超容（在册数 > 容量）。 */
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Boolean overCapacity;

    /** 超容说明：在册数超过容量时给出原因和处置要求，不超时为 null。 */
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String capacityNote;
}
