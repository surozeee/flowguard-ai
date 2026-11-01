export interface GlobalResponse<T> {
  status: string;
  message: string;
  data: T;
}
