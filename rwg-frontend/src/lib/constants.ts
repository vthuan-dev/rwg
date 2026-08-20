/**
 * Constants & API Configuration for RWG Frontend
 */

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
export const ADMIN_API_BASE_URL = process.env.NEXT_PUBLIC_ADMIN_API_URL || 'http://localhost:8081';
export const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8080/ws';

export const KL28_ODDS = {
  BIG: '1.98',
  SMALL: '1.98',
  SINGLE: '1.98',
  DOUBLE: '1.98',
  NUMBER_ODDS: [
    '999', '332', '165', '99', '65', '46', '34', '26', '21', '17',
    '14', '13', '12', '12', '12', '12', '13', '14', '17', '21',
    '26', '34', '46', '65', '99', '165', '332', '999'
  ]
} as const;
