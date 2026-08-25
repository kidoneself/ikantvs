package com.jyinshi.transfer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 每盘两个当前号：追更号 / 片库号。可指向同一账号。 */
@Data
@TableName("transfer_pan_pointer")
public class TransferPanPointer implements Serializable {

    @TableId(type = IdType.INPUT)
    private String panType;

    /** 每日更新巡检账号名。 */
    private String followAccountName;
    /** 自营录入片库账号名。 */
    private String libraryAccountName;

    private LocalDateTime updatedAt;
}
