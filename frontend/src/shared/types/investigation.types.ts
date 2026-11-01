export interface InvestigationSummary {
  id: string;
  state: string;
  modelId?: string;
  maxProbes?: number;
  probesUsed?: number;
  objective?: string;
  errorCode?: string;
}

export interface StartInvestigationRequest {
  projectId: string;
  objective: string;
  maxProbes?: number;
  modelId?: string;
}

export interface StartInvestigationResponse {
  id: string;
  state: string;
  modelId: string;
  maxProbes: number;
  probesUsed: number;
  eventsUrl: string;
}

export interface ExperimentSummary {
  id: string;
  experimentType?: string;
  status?: string;
  oracleVerdict?: string;
  hypothesis?: string;
}
