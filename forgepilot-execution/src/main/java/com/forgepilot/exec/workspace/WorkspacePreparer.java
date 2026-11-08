package com.forgepilot.exec.workspace;

/**
 * 为一个任务准备独立工作区，并把项目的每个仓库检出到稳定子目录。
 * 不得复用其他任务的可写目录。
 */
public interface WorkspacePreparer {

    void prepare(String taskId);
}
