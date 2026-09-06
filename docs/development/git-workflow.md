# Git 工作流

- 从目标分支创建短生命周期分支，建议使用 `feature/`、`fix/`、`docs/` 或团队约定前缀。
- 每个提交保持单一目的，提交前检查 `git diff` 和 `git status`。
- 不覆盖其他人的工作区修改；发现已有修改时先区分归属。
- 合并前完成测试和文档同步，必要时在变更说明中列出兼容性影响。
- 发布标签使用 `v<major>.<minor>.<patch>`，标签版本必须与根 POM、README 和 CHANGELOG 一致。
