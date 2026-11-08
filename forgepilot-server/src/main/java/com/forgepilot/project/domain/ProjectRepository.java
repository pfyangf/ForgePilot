package com.forgepilot.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 项目上的一条仓库连接。凭证不在本对象中，而在集成密文表。
 */
@Getter
@Setter
@TableName("project_repository")
public class ProjectRepository {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String providerType;
    private String displayName;
    private String cloneUrl;
    private String localDirectoryName;
    private String defaultBranch;
    private String externalId;
    private String createdBy;
    private String updatedBy;
}
