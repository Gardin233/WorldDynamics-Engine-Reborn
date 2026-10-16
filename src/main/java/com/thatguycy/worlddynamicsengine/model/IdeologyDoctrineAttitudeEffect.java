package com.thatguycy.worlddynamicsengine.model;

/**
 * 定义戒律态度效果的注册标识契约，具体执行行为由效果实现方负责。
 */
public interface IdeologyDoctrineAttitudeEffect {
    String getDoctrineId();

    String getAttitudeId();

    default String getEffectId() {
        return getClass().getName();
    }
}
