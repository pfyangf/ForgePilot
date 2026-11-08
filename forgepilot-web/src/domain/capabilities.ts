/** 与执行服务 CodexCliAgentAdapter 的声明保持一致。 */
export interface AgentCapabilities {
  interactiveInput: boolean
  nativePause: boolean
}

export const codexCapabilities: AgentCapabilities = {
  interactiveInput: false,
  nativePause: false,
}
