package com.jyinshi.transfer.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 某盘的追更号 / 片库号指针。 */
@Data
public class PanPointerVO {

    private String panType;
    private String panLabel;
    private String followAccountName;
    private String libraryAccountName;
    private List<String> accountNames = new ArrayList<>();
}
