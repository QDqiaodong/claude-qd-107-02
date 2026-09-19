package com.archive.center.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/** 调阅登记：谁把哪一卷借去看了，什么时候还回来。 */
@Entity
@Table(name = "retrieval")
public class Retrieval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "archive_id", nullable = false)
    public Long archiveId;

    /** 调阅人 */
    @Column(nullable = false, length = 32)
    public String visitor;

    /** 调阅人所在单位 */
    @Column(nullable = false, length = 64)
    public String dept;

    @Column(name = "retrieve_date", nullable = false)
    public LocalDate retrieveDate;

    /** 应还日期 */
    @Column(name = "due_date", nullable = false)
    public LocalDate dueDate;

    @Column(name = "return_date")
    public LocalDate returnDate;

    /** 调阅中 / 已归还 */
    @Column(nullable = false, length = 16)
    public String status;
}
