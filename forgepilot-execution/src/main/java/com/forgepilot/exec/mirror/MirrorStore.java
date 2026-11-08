package com.forgepilot.exec.mirror;

/** 按仓库维护 bare mirror。mirror 只读共享，不作为任务的可写工作副本。 */
public interface MirrorStore {

    void fetch(String repositoryId);
}
