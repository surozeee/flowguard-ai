import { apiFetch } from "@/lib/api-fetch";

export interface ProjectSummary {
  id: string;
  slug: string;
  fixtureVersion: string;
}

export interface InvariantSummary {
  id: string;
  description: string;
}

export const projectService = {
  list: () => apiFetch<ProjectSummary[]>("/projects"),
  getBySlug: (slug: string) => apiFetch<ProjectSummary>(`/projects/${slug}`),
  listInvariants: (slug: string) => apiFetch<InvariantSummary[]>(`/projects/${slug}/invariants`),
};
