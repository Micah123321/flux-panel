export type DiagnosisProtocol = "tcp" | "udp";
export type DiagnosisStatus = "reachable" | "unreachable" | "unknown";

export interface DiagnosisResult {
  forwardName: string;
  protocol?: DiagnosisProtocol;
  timestamp: number;
  results: Array<{
    success: boolean;
    protocol?: DiagnosisProtocol;
    status?: DiagnosisStatus;
    description: string;
    nodeName: string;
    nodeId: string | number;
    targetIp: string;
    targetPort?: number;
    message?: string;
    averageTime?: number | null;
    packetLoss?: number | null;
    sentPackets?: number;
    receivedPackets?: number;
  }>;
}

export const normalizeDiagnosisPayload = (value: string) => value.replace(/\s/g, "");

export const getDiagnosisPayloadError = (value: string): string | undefined => {
  const payload = normalizeDiagnosisPayload(value);
  if (payload.length > 2048) return "探测数据最多为 2048 个十六进制字符（1024 字节）";
  if (!/^[0-9a-f]*$/i.test(payload)) return "请仅输入十六进制字符（0-9、A-F），可使用空格分隔";
  if (payload.length % 2 !== 0) return "每个字节需要两个十六进制字符";
  return undefined;
};

export const normalizeDiagnosisReport = (
  report: DiagnosisResult,
  requestedProtocol: DiagnosisProtocol,
): DiagnosisResult => ({
  ...report,
  protocol: report.protocol ?? (requestedProtocol === "tcp" ? "tcp" : undefined),
  results: report.results.map((result) => {
    const protocol = result.protocol ?? (
      requestedProtocol === "tcp" && report.protocol !== "udp" ? "tcp" : undefined
    );
    // A legacy TCP response cannot confirm a UDP probe, even when success is true.
    const unconfirmedUdp = requestedProtocol === "udp" && (
      report.protocol !== "udp" || result.protocol === undefined
    );
    const udpExecutionIssue = protocol === "udp" && result.status !== "reachable" && /超时|timeout|timed out|不支持|未支持|unsupported|not supported|not implemented|节点.*(?:离线|未连接)/i.test(result.message ?? "");
    const status: DiagnosisStatus = unconfirmedUdp || udpExecutionIssue
      ? "unknown"
      : result.status ?? (protocol === "tcp" ? (result.success ? "reachable" : "unreachable") : "unknown");

    return {
      ...result,
      protocol,
      status,
      success: status === "reachable",
      message: unconfirmedUdp
        ? "当前服务尚未确认支持本次 UDP 诊断，返回结果未确认 UDP 目标是否可达。"
        : result.message || (status === "unknown" ? "尚未确认目标是否可达；等待回包超时不代表目标不可达。" : undefined),
    };
  }),
});
