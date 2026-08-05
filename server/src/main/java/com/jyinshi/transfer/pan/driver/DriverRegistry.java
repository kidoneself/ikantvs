package com.jyinshi.transfer.pan.driver;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按网盘类型查找 driver。Spring 自动收集所有 {@link PanDriver} 实现。 */
@Component
public class DriverRegistry {

    private final Map<PanType, PanDriver> drivers = new EnumMap<>(PanType.class);

    public DriverRegistry(List<PanDriver> all) {
        for (PanDriver d : all) {
            drivers.put(d.type(), d);
        }
    }

    public PanDriver get(PanType type) {
        return drivers.get(type);
    }

    public boolean supports(PanType type) {
        return type != null && drivers.containsKey(type);
    }

    /** 本机已装载的网盘类型（用于向队列领取对应类型的任务）。 */
    public Set<PanType> types() {
        return drivers.keySet();
    }
}
