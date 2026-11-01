/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_FLOWGUARD_API_KEY?: string;
  readonly VITE_API_GATEWAY_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
