/**
 * TypeScript definitions matching RWG Spring Boot Backend DTOs
 */

export type Role = 'PLAYER' | 'ADMIN' | 'FINANCE' | 'SUPPORT' | 'RISK';
export type KycLevel = 'NONE' | 'LEVEL_1' | 'LEVEL_2' | 'LEVEL_3';

export interface UserProfile {
  id: string;
  username: string;
  email: string;
  role: Role;
  kycLevel: KycLevel;
  locale: string;
  hasWithdrawalPassword: boolean;
  createdAt: string;
}

export interface WalletBalance {
  walletId: string;
  userId: string;
  currency: string;
  balance: string; // BigDecimal as string
}

export interface GameTable {
  id: string;
  nameI18n: Record<string, string>; // { en: string, vi: string, ... }
  gameType: 'KL28' | 'ROULETTE' | 'BACCARAT';
  minBet: string;
  maxBet: string;
  status: 'ACTIVE' | 'DISABLED';
}

export interface GameRound {
  id: string;
  tableId: string;
  roundSeq: number;
  phase: 'BETTING' | 'RESOLVING' | 'SETTLED';
  status: 'OPEN' | 'SETTLED' | 'VOIDED';
  winningNumber?: number;
  kl28Numbers?: number[]; // [n1, n2, n3]
  kl28Sum?: number;       // n1 + n2 + n3
  serverTime: string;
}

export type Kl28BetType = 'KL28_BIG' | 'KL28_SMALL' | 'KL28_SINGLE' | 'KL28_DOUBLE' | 'KL28_NUMBER';

export interface BetRequest {
  betType: Kl28BetType;
  selection: string; // "" for Big/Small/Single/Double, "0".."27" for NUMBER
  stake: string;
  idempotencyKey: string;
}

export interface BetResponse {
  betId: string;
  tableId: string;
  roundId: string;
  betType: string;
  selection: string;
  stake: string;
  status: string;
  createdAt: string;
}

export interface PlayerBetResponse {
  betId: string;
  roundId: string;
  tableId: string;
  betType: string;
  selection: string;
  stake: string;
  status: string;
  payout: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
