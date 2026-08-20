# LOGIC GAME, HỆ THỐNG HOA HỒNG & QUẢN TRỊ ADMIN

## MỤC LỤC
- [PHẦN 1: LOGIC CHƠI GAME CHO NGƯỜI CHƠI](#phan-1)
- [PHẦN 2: HỆ THỐNG HOA HỒNG](#phan-2)
- [PHẦN 3: ADMIN QUẢN LÝ](#phan-3)

---

# PHẦN 1: LOGIC CHƠI GAME CHO NGƯỜI CHƠI {#phan-1}

## 1. LUỒNG CHƠI GAME TỔNG QUÁT

### 1.1. Flow Chart Tổng Quát
```
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 1: NGƯỜI CHƠI VÀO TRANG CHỦ                            │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 2: CHỌN LOẠI GAME                                       │
│  ○ Live Casino (Roulette, Baccarat, Blackjack, Poker)      │
│  ○ Slot Games (Lucky 28, Video Slots, Jackpot)             │
│  ○ Lottery/Lucky Numbers                                    │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 3: KIỂM TRA YÊU CẦU                                     │
│  ✓ Đã đăng nhập?                                            │
│  ✓ Đã KYC? (nếu cần)                                        │
│  ✓ Đủ balance?                                              │
│  ✓ Đủ VIP level? (cho table cao cấp)                       │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 4: CHỌN BÀN/PHÒNG CHƠI                                  │
│  • Xem thông tin bàn (min/max bet, dealer, players)        │
│  • Xem lịch sử kết quả (roadmap, statistics)               │
│  • Chọn và join table                                       │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 5: ĐẶT CƯỢC (BETTING PHASE)                            │
│  • Thời gian đặt cược: 30-60s                               │
│  • Chọn chip denomination                                   │
│  • Click vào vị trí muốn đặt                                │
│  • System check balance real-time                           │
│  • Có thể undo, clear, rebet                                │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 6: KHÓA CƯỢC ("NO MORE BETS")                          │
│  • Hết thời gian đặt cược                                   │
│  • System lock tất cả bets                                  │
│  • Tính tổng bet amount                                     │
│  • Trừ balance ngay lập tức                                 │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 7: XỬ LÝ GAME (DEALER HOẶC RNG)                       │
│  • Live: Dealer thực hiện (spin, deal cards, etc.)         │
│  • Slots: RNG tính toán kết quả                             │
│  • Player xem live stream / animation                       │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 8: CÔNG BỐ KẾT QUẢ                                     │
│  • Hiển thị winning number/cards                            │
│  • Highlight winning bets                                   │
│  • Show win amount                                          │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 9: TÍNH TOÁN & THANH TOÁN                              │
│  • System tự động tính payout theo odds                     │
│  • Cộng tiền thắng vào balance                              │
│  • Trừ commission (nếu có, ví dụ: Banker win Baccarat)     │
│  • Cập nhật loyalty points                                  │
│  • Ghi log transaction                                      │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 10: CẬP NHẬT LỊCH SỬ & STATS                           │
│  • Lưu vào bet history                                      │
│  • Cập nhật game statistics                                 │
│  • Update leaderboard (nếu có)                              │
│  • Send notification (nếu big win)                          │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ BƯỚC 11: VÒNG MỚI HOẶC RỜI BÀN                              │
│  • Người chơi có thể:                                        │
│    ○ Tiếp tục chơi vòng mới (loop về Bước 5)               │
│    ○ Đổi bàn (về Bước 4)                                    │
│    ○ Đổi game (về Bước 2)                                   │
│    ○ Rời khỏi game                                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. LOGIC CHI TIẾT TỪNG LOẠI GAME

### 2.1. LIVE ROULETTE - LOGIC HOÀN CHỈNH

#### A. Pre-Game (Trước khi vào bàn)
```javascript
// Kiểm tra điều kiện
function canJoinRouletteTable(player, table) {
  // 1. Check player logged in
  if (!player.isAuthenticated) {
    return { allowed: false, reason: "Please login first" };
  }
  
  // 2. Check KYC verified
  if (table.requiresKYC && !player.kycVerified) {
    return { allowed: false, reason: "KYC verification required" };
  }
  
  // 3. Check VIP level
  if (table.minVipLevel > player.vipLevel) {
    return { allowed: false, reason: `VIP ${table.minVipLevel} required` };
  }
  
  // 4. Check minimum balance
  if (player.balance < table.minBet * 5) {
    return { allowed: false, reason: "Insufficient balance" };
  }
  
  // 5. Check geo-restrictions
  if (table.restrictedCountries.includes(player.country)) {
    return { allowed: false, reason: "Not available in your country" };
  }
  
  // 6. Check table capacity
  if (table.currentPlayers >= table.maxPlayers) {
    return { allowed: false, reason: "Table is full" };
  }
  
  return { allowed: true };
}
```

#### B. Betting Phase (Giai đoạn đặt cược)
```javascript
// 1. Khởi tạo vòng chơi mới
function startNewRound(table) {
  const round = {
    id: generateUniqueRoundId(),
    tableId: table.id,
    startTime: Date.now(),
    bettingDuration: 45000, // 45 giây
    status: "BETTING_OPEN",
    bets: [],
    result: null
  };
  
  // Broadcast to all players
  broadcastToTable(table.id, {
    type: "ROUND_STARTED",
    round: round,
    countdown: round.bettingDuration
  });
  
  // Set timer
  setTimeout(() => closeBetting(round), round.bettingDuration);
  
  return round;
}

// 2. Người chơi đặt cược
function placeBet(playerId, roundId, betData) {
  const player = getPlayer(playerId);
  const round = getRound(roundId);
  
  // Validation
  const validation = validateBet(player, round, betData);
  if (!validation.valid) {
    return { success: false, error: validation.error };
  }
  
  // Tính tổng số tiền cần đặt
  const totalBetAmount = calculateTotalBet(betData);
  
  // Check balance
  if (player.balance < totalBetAmount) {
    return { success: false, error: "Insufficient balance" };
  }
  
  // Khóa tiền (deduct from balance)
  const transaction = {
    id: generateTransactionId(),
    playerId: playerId,
    roundId: roundId,
    type: "BET",
    amount: -totalBetAmount,
    balanceBefore: player.balance,
    balanceAfter: player.balance - totalBetAmount,
    status: "LOCKED",
    timestamp: Date.now()
  };
  
  // Cập nhật database (ATOMIC operation)
  db.transaction(() => {
    // Trừ balance
    db.updatePlayerBalance(playerId, -totalBetAmount);
    
    // Lưu bet
    db.saveBet({
      roundId: roundId,
      playerId: playerId,
      bets: betData, // [{position: "RED", amount: 100}, {position: "17", amount: 50}]
      totalAmount: totalBetAmount,
      transactionId: transaction.id,
      timestamp: Date.now()
    });
    
    // Lưu transaction
    db.saveTransaction(transaction);
  });
  
  // Update player's session
  player.balance -= totalBetAmount;
  player.activeBets.push(transaction.id);
  
  // Broadcast bet to table (optional, show other players' bets)
  broadcastToTable(round.tableId, {
    type: "BET_PLACED",
    playerId: playerId,
    amount: totalBetAmount
  });
  
  return { 
    success: true, 
    transactionId: transaction.id,
    newBalance: player.balance 
  };
}

// 3. Validate bet
function validateBet(player, round, betData) {
  // Check round status
  if (round.status !== "BETTING_OPEN") {
    return { valid: false, error: "Betting is closed" };
  }
  
  // Check bet structure
  if (!betData || betData.length === 0) {
    return { valid: false, error: "No bets specified" };
  }
  
  // Check each bet
  for (const bet of betData) {
    // Valid position?
    if (!isValidRoulettePosition(bet.position)) {
      return { valid: false, error: `Invalid position: ${bet.position}` };
    }
    
    // Min/Max bet limits
    const limits = getTableLimits(round.tableId, bet.position);
    if (bet.amount < limits.min) {
      return { valid: false, error: `Minimum bet is ${limits.min}` };
    }
    if (bet.amount > limits.max) {
      return { valid: false, error: `Maximum bet is ${limits.max}` };
    }
  }
  
  // Check total bet limit
  const totalBet = betData.reduce((sum, bet) => sum + bet.amount, 0);
  const tableMaxBet = getTableMaxBet(round.tableId);
  if (totalBet > tableMaxBet) {
    return { valid: false, error: `Total bet exceeds table limit` };
  }
  
  return { valid: true };
}
```

#### C. Spinning Phase (Quay số)
```javascript
// 1. Đóng betting
function closeBetting(round) {
  round.status = "BETTING_CLOSED";
  round.bettingEndTime = Date.now();
  
  // Broadcast
  broadcastToTable(round.tableId, {
    type: "NO_MORE_BETS",
    message: "No more bets!"
  });
  
  // Dealer bắt đầu quay
  setTimeout(() => spinWheel(round), 2000); // 2s delay
}

// 2. Quay wheel (thực tế hoặc RNG)
function spinWheel(round) {
  round.status = "SPINNING";
  
  // Nếu live: Dealer quay thật
  // Nếu auto: RNG generate result
  
  // For live casino: wait for physical result
  // For auto roulette: generate immediately
  
  if (round.table.type === "AUTO") {
    // RNG generate
    const result = generateRouletteResult();
    setTimeout(() => announceResult(round, result), 8000); // 8s spinning
  } else {
    // Live: wait for dealer input or camera detection
    waitForLiveResult(round);
  }
}

// 3. Generate RNG result (European Roulette: 0-36)
function generateRouletteResult() {
  // RNG certified algorithm
  const randomNumber = cryptoRandomInt(0, 37); // 0-36
  
  return {
    number: randomNumber,
    color: getNumberColor(randomNumber),
    properties: getNumberProperties(randomNumber)
  };
}

function getNumberColor(number) {
  if (number === 0) return "GREEN";
  
  const redNumbers = [1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36];
  return redNumbers.includes(number) ? "RED" : "BLACK";
}

function getNumberProperties(number) {
  return {
    isZero: number === 0,
    isRed: getNumberColor(number) === "RED",
    isBlack: getNumberColor(number) === "BLACK",
    isEven: number !== 0 && number % 2 === 0,
    isOdd: number !== 0 && number % 2 === 1,
    isLow: number >= 1 && number <= 18,
    isHigh: number >= 19 && number <= 36,
    dozen: number === 0 ? null : Math.ceil(number / 12),
    column: number === 0 ? null : ((number - 1) % 3) + 1
  };
}
```

#### D. Result & Payout (Kết quả & thanh toán)
```javascript
// 1. Công bố kết quả
function announceResult(round, result) {
  round.status = "RESULT";
  round.result = result;
  round.resultTime = Date.now();
  
  // Save result
  db.saveRoundResult(round.id, result);
  
  // Broadcast
  broadcastToTable(round.tableId, {
    type: "RESULT_ANNOUNCED",
    result: result,
    message: `${result.number} ${result.color}`
  });
  
  // Calculate and process all payouts
  setTimeout(() => processPayouts(round), 3000); // 3s để player xem
}

// 2. Tính toán payouts
function processPayouts(round) {
  const allBets = db.getRoundBets(round.id);
  const result = round.result;
  
  const payoutPromises = allBets.map(playerBet => 
    calculateAndPayPlayer(playerBet, result)
  );
  
  Promise.all(payoutPromises).then(results => {
    // Broadcast winners
    const winners = results.filter(r => r.won);
    broadcastToTable(round.tableId, {
      type: "PAYOUTS_COMPLETE",
      winners: winners.map(w => ({
        playerId: w.playerId,
        amount: w.winAmount
      }))
    });
    
    // Start new round
    setTimeout(() => startNewRound(round.table), 5000);
  });
}

// 3. Tính payout cho từng người chơi
async function calculateAndPayPlayer(playerBet, result) {
  let totalWin = 0;
  const winningBets = [];
  
  // Check từng bet
  for (const bet of playerBet.bets) {
    const isWin = checkBetWin(bet.position, result);
    
    if (isWin) {
      const payout = calculatePayout(bet.position, bet.amount);
      totalWin += payout;
      winningBets.push({
        position: bet.position,
        betAmount: bet.amount,
        payout: payout
      });
    }
  }
  
  if (totalWin > 0) {
    // Người chơi thắng
    return await payWinner(playerBet.playerId, playerBet.roundId, totalWin, winningBets);
  } else {
    // Người chơi thua (tiền đã bị trừ lúc đặt cược)
    return await recordLoss(playerBet.playerId, playerBet.roundId, playerBet.totalAmount);
  }
}

// 4. Check bet có thắng không
function checkBetWin(position, result) {
  // Straight up (single number)
  if (typeof position === 'number') {
    return position === result.number;
  }
  
  // String positions
  switch(position.toUpperCase()) {
    case 'RED':
      return result.color === 'RED';
    case 'BLACK':
      return result.color === 'BLACK';
    case 'EVEN':
      return result.properties.isEven;
    case 'ODD':
      return result.properties.isOdd;
    case 'LOW':
    case '1-18':
      return result.properties.isLow;
    case 'HIGH':
    case '19-36':
      return result.properties.isHigh;
    case 'DOZEN_1':
      return result.properties.dozen === 1;
    case 'DOZEN_2':
      return result.properties.dozen === 2;
    case 'DOZEN_3':
      return result.properties.dozen === 3;
    case 'COLUMN_1':
      return result.properties.column === 1;
    case 'COLUMN_2':
      return result.properties.column === 2;
    case 'COLUMN_3':
      return result.properties.column === 3;
    // Split, Street, Corner, etc.
    default:
      return checkComplexBet(position, result.number);
  }
}

// 5. Tính payout odds
function calculatePayout(position, betAmount) {
  const odds = getPayoutOdds(position);
  return betAmount * odds; // không bao gồm bet gốc, chỉ tính win
}

function getPayoutOdds(position) {
  // Roulette payout table
  const payouts = {
    // Straight up
    'STRAIGHT': 35,
    
    // Split (2 numbers)
    'SPLIT': 17,
    
    // Street (3 numbers)
    'STREET': 11,
    
    // Corner (4 numbers)
    'CORNER': 8,
    
    // Six line (6 numbers)
    'SIXLINE': 5,
    
    // Dozen, Column
    'DOZEN': 2,
    'COLUMN': 2,
    
    // Even money bets
    'RED': 1,
    'BLACK': 1,
    'EVEN': 1,
    'ODD': 1,
    'LOW': 1,
    'HIGH': 1
  };
  
  // Determine bet type and return odds
  // ...
  
  return odds;
}

// 6. Trả tiền thắng
async function payWinner(playerId, roundId, winAmount, winningBets) {
  const player = getPlayer(playerId);
  
  const transaction = {
    id: generateTransactionId(),
    playerId: playerId,
    roundId: roundId,
    type: "WIN",
    amount: winAmount,
    balanceBefore: player.balance,
    balanceAfter: player.balance + winAmount,
    details: winningBets,
    timestamp: Date.now()
  };
  
  // Atomic update
  await db.transaction(async () => {
    // Cộng tiền
    await db.updatePlayerBalance(playerId, winAmount);
    
    // Lưu transaction
    await db.saveTransaction(transaction);
    
    // Update bet record
    await db.updateBetResult(playerId, roundId, {
      status: "WON",
      winAmount: winAmount,
      settledAt: Date.now()
    });
    
    // Award loyalty points
    const points = Math.floor(winAmount * 0.01); // 1% as points
    await db.addLoyaltyPoints(playerId, points);
  });
  
  // Update player session
  player.balance += winAmount;
  
  // Send notification
  sendNotification(playerId, {
    type: "BIG_WIN",
    amount: winAmount,
    game: "Roulette"
  });
  
  return {
    won: true,
    playerId: playerId,
    winAmount: winAmount
  };
}

// 7. Ghi nhận thua
async function recordLoss(playerId, roundId, lossAmount) {
  await db.updateBetResult(playerId, roundId, {
    status: "LOST",
    lossAmount: lossAmount,
    settledAt: Date.now()
  });
  
  // Check for cashback eligibility
  checkCashbackEligibility(playerId, lossAmount);
  
  return {
    won: false,
    playerId: playerId
  };
}
```

---

### 2.2. LIVE BACCARAT - LOGIC HOÀN CHỈNH

```javascript
// 1. Start Round
function startBaccaratRound(table) {
  const round = {
    id: generateUniqueRoundId(),
    tableId: table.id,
    gameNumber: getNextGameNumber(table),
    status: "BETTING_OPEN",
    bettingDuration: 15000, // 15s
    bets: [],
    cards: {
      player: [],
      banker: []
    },
    result: null
  };
  
  broadcastToTable(table.id, {
    type: "BACCARAT_ROUND_START",
    round: round
  });
  
  setTimeout(() => closeBaccaratBetting(round), round.bettingDuration);
  
  return round;
}

// 2. Place Bet (Player, Banker, Tie)
function placeBaccaratBet(playerId, roundId, betData) {
  // betData = { type: "BANKER", amount: 100 }
  // hoặc multiple: [
  //   { type: "BANKER", amount: 100 },
  //   { type: "PLAYER_PAIR", amount: 20 }
  // ]
  
  const validation = validateBaccaratBet(playerId, roundId, betData);
  if (!validation.valid) {
    return { success: false, error: validation.error };
  }
  
  // Process bet (tương tự Roulette)
  // ...
}

// 3. Deal Cards
function dealBaccaratCards(round) {
  round.status = "DEALING";
  
  // Shuffle deck (RNG)
  const deck = generateShuffledDeck();
  
  // Deal 2 cards to Player
  round.cards.player.push(deck.pop());
  round.cards.player.push(deck.pop());
  
  // Deal 2 cards to Banker
  round.cards.banker.push(deck.pop());
  round.cards.banker.push(deck.pop());
  
  // Calculate totals
  const playerTotal = calculateBaccaratTotal(round.cards.player);
  const bankerTotal = calculateBaccaratTotal(round.cards.banker);
  
  // Broadcast cards
  broadcastToTable(round.tableId, {
    type: "CARDS_DEALT",
    playerCards: round.cards.player,
    bankerCards: round.cards.banker
  });
  
  // Check for natural win (8 or 9)
  if (playerTotal >= 8 || bankerTotal >= 8) {
    // Natural win, no third card
    setTimeout(() => settleBaccaratRound(round), 2000);
  } else {
    // Check third card rules
    setTimeout(() => dealThirdCards(round, playerTotal, bankerTotal), 3000);
  }
}

// 4. Third Card Rules
function dealThirdCards(round, playerTotal, bankerTotal) {
  let playerThirdCard = null;
  
  // Player third card rule
  if (playerTotal <= 5) {
    playerThirdCard = deck.pop();
    round.cards.player.push(playerThirdCard);
    
    broadcastToTable(round.tableId, {
      type: "THIRD_CARD",
      side: "PLAYER",
      card: playerThirdCard
    });
  }
  
  // Banker third card rule (depends on player's third card)
  const shouldBankerDraw = determineBankerThirdCard(
    bankerTotal, 
    playerThirdCard ? getCardValue(playerThirdCard) : null
  );
  
  if (shouldBankerDraw) {
    const bankerThirdCard = deck.pop();
    round.cards.banker.push(bankerThirdCard);
    
    broadcastToTable(round.tableId, {
      type: "THIRD_CARD",
      side: "BANKER",
      card: bankerThirdCard
    });
  }
  
  // Settle round
  setTimeout(() => settleBaccaratRound(round), 2000);
}

// 5. Calculate Total
function calculateBaccaratTotal(cards) {
  let total = 0;
  
  for (const card of cards) {
    const value = getCardValue(card);
    // 10, J, Q, K = 0
    // A = 1
    // Others = face value
    if (value >= 10) {
      total += 0;
    } else {
      total += value;
    }
  }
  
  // Only last digit counts
  return total % 10;
}

// 6. Settle Round & Pay
function settleBaccaratRound(round) {
  const playerTotal = calculateBaccaratTotal(round.cards.player);
  const bankerTotal = calculateBaccaratTotal(round.cards.banker);
  
  let winner;
  if (playerTotal > bankerTotal) {
    winner = "PLAYER";
  } else if (bankerTotal > playerTotal) {
    winner = "BANKER";
  } else {
    winner = "TIE";
  }
  
  round.result = {
    winner: winner,
    playerTotal: playerTotal,
    bankerTotal: bankerTotal,
    playerPair: isPair(round.cards.player.slice(0, 2)),
    bankerPair: isPair(round.cards.banker.slice(0, 2))
  };
  
  // Broadcast result
  broadcastToTable(round.tableId, {
    type: "BACCARAT_RESULT",
    result: round.result
  });
  
  // Process payouts
  processBaccaratPayouts(round);
}

// 7. Baccarat Payouts
async function processBaccaratPayouts(round) {
  const allBets = db.getRoundBets(round.id);
  
  for (const playerBet of allBets) {
    let totalWin = 0;
    
    for (const bet of playerBet.bets) {
      let payout = 0;
      
      switch(bet.type) {
        case "PLAYER":
          if (round.result.winner === "PLAYER") {
            payout = bet.amount * 1; // 1:1
          }
          break;
          
        case "BANKER":
          if (round.result.winner === "BANKER") {
            payout = bet.amount * 0.95; // 1:1 minus 5% commission
          }
          break;
          
        case "TIE":
          if (round.result.winner === "TIE") {
            payout = bet.amount * 8; // 8:1
          }
          break;
          
        case "PLAYER_PAIR":
          if (round.result.playerPair) {
            payout = bet.amount * 11; // 11:1
          }
          break;
          
        case "BANKER_PAIR":
          if (round.result.bankerPair) {
            payout = bet.amount * 11; // 11:1
          }
          break;
      }
      
      totalWin += payout;
    }
    
    if (totalWin > 0) {
      await payWinner(playerBet.playerId, round.id, totalWin, playerBet.bets);
    }
  }
  
  // Update roadmap
  updateBaccaratRoadmap(round.tableId, round.result);
  
  // Start new round
  setTimeout(() => startBaccaratRound(round.table), 5000);
}
```

---

### 2.3. SLOT GAMES - LOGIC HOÀN CHỈNH

```javascript
// 1. Khởi tạo game session
function initSlotGame(playerId, gameId) {
  const game = getGameConfig(gameId);
  const player = getPlayer(playerId);
  
  // Create session
  const session = {
    id: generateSessionId(),
    playerId: playerId,
    gameId: gameId,
    startTime: Date.now(),
    totalSpins: 0,
    totalWagered: 0,
    totalWon: 0,
    biggestWin: 0,
    featuresTriggers: 0,
    status: "ACTIVE"
  };
  
  db.saveGameSession(session);
  
  return {
    success: true,
    session: session,
    gameConfig: {
      id: game.id,
      name: game.name,
      reels: game.reels, // số reels (thường 5)
      rows: game.rows, // số rows (thường 3)
      paylines: game.paylines, // số paylines
      rtp: game.rtp, // 96.5%
      volatility: game.volatility, // LOW, MEDIUM, HIGH
      minBet: game.minBet,
      maxBet: game.maxBet,
      coinValues: game.coinValues, // [0.01, 0.05, 0.10, 0.25, 0.50, 1.00]
      features: game.features, // [FREE_SPINS, WILD, SCATTER, MULTIPLIER]
      paytable: game.paytable
    }
  };
}

// 2. Player spin
async function spinSlot(sessionId, betConfig) {
  const session = getSession(sessionId);
  const player = getPlayer(session.playerId);
  const game = getGameConfig(session.gameId);
  
  // betConfig = {
  //   coinValue: 0.10,
  //   coinsPerLine: 1,
  //   activePaylines: 20
  // }
  
  const totalBet = betConfig.coinValue * betConfig.coinsPerLine * betConfig.activePaylines;
  
  // Validate
  if (totalBet < game.minBet || totalBet > game.maxBet) {
    return { success: false, error: "Invalid bet amount" };
  }
  
  if (player.balance < totalBet) {
    return { success: false, error: "Insufficient balance" };
  }
  
  // Deduct bet
  await db.updatePlayerBalance(player.id, -totalBet);
  player.balance -= totalBet;
  
  // Generate spin result using RNG
  const spinResult = generateSlotResult(game, betConfig);
  
  // Calculate wins
  const winResult = calculateSlotWins(game, spinResult, betConfig);
  
  // Pay winnings
  if (winResult.totalWin > 0) {
    await db.updatePlayerBalance(player.id, winResult.totalWin);
    player.balance += winResult.totalWin;
  }
  
  // Save spin
  const spin = {
    id: generateSpinId(),
    sessionId: sessionId,
    playerId: player.id,
    gameId: game.id,
    betAmount: totalBet,
    betConfig: betConfig,
    result: spinResult,
    winAmount: winResult.totalWin,
    winDetails: winResult.wins,
    balanceAfter: player.balance,
    timestamp: Date.now()
  };
  
  await db.saveSpin(spin);
  
  // Update session stats
  session.totalSpins++;
  session.totalWagered += totalBet;
  session.totalWon += winResult.totalWin;
  if (winResult.totalWin > session.biggestWin) {
    session.biggestWin = winResult.totalWin;
  }
  
  // Check for feature trigger
  if (spinResult.featureTriggered) {
    session.featuresTriggers++;
    return await triggerSlotFeature(session, spin, spinResult.feature);
  }
  
  return {
    success: true,
    spin: {
      id: spin.id,
      reels: spinResult.reels,
      winAmount: winResult.totalWin,
      winLines: winResult.wins,
      balance: player.balance
    }
  };
}

// 3. RNG Generate slot result
function generateSlotResult(game, betConfig) {
  const reels = [];
  
  // Generate each reel
  for (let reelIndex = 0; reelIndex < game.reels; reelIndex++) {
    const reel = [];
    const reelStrip = game.reelStrips[reelIndex]; // pre-configured symbol strips
    
    // Random stop position
    const stopPosition = cryptoRandomInt(0, reelStrip.length);
    
    // Get symbols for display (3 symbols per reel)
    for (let row = 0; row < game.rows; row++) {
      const symbolIndex = (stopPosition + row) % reelStrip.length;
      reel.push(reelStrip[symbolIndex]);
    }
    
    reels.push(reel);
  }
  
  // Check for scatter symbols (can trigger free spins)
  const scatterCount = countScatters(reels, game.scatterSymbol);
  
  const result = {
    reels: reels,
    featureTriggered: scatterCount >= 3,
    feature: scatterCount >= 3 ? {
      type: "FREE_SPINS",
      count: getFreeSpinsCount(scatterCount) // 3 scatters = 10 spins, 4 = 15, 5 = 20
    } : null
  };
  
  return result;
}

// 4. Calculate wins
function calculateSlotWins(game, spinResult, betConfig) {
  const wins = [];
  let totalWin = 0;
  
  // Check each active payline
  for (let lineNum = 0; lineNum < betConfig.activePaylines; lineNum++) {
    const payline = game.paylinePatterns[lineNum]; // e.g., [1,1,1,1,1] = middle row
    
    // Get symbols on this payline
    const lineSymbols = [];
    for (let reelIndex = 0; reelIndex < game.reels; reelIndex++) {
      const row = payline[reelIndex];
      lineSymbols.push(spinResult.reels[reelIndex][row]);
    }
    
    // Check for winning combination
    const winCheck = checkPaylineWin(lineSymbols, game.paytable);
    
    if (winCheck.win) {
      const betPerLine = betConfig.coinValue * betConfig.coinsPerLine;
      const winAmount = winCheck.multiplier * betPerLine;
      
      wins.push({
        payline: lineNum,
        symbols: winCheck.symbols,
        count: winCheck.count,
        multiplier: winCheck.multiplier,
        amount: winAmount
      });
      
      totalWin += winAmount;
    }
  }
  
  // Check for scatter wins (pay anywhere)
  const scatterWin = checkScatterWin(spinResult.reels, game);
  if (scatterWin > 0) {
    totalWin += scatterWin * betConfig.coinValue * betConfig.activePaylines;
  }
  
  return {
    totalWin: totalWin,
    wins: wins
  };
}

// 5. Check payline win
function checkPaylineWin(symbols, paytable) {
  // Check from left to right
  let matchCount = 1;
  const firstSymbol = symbols[0];
  
  // Wild substitution
  let effectiveSymbol = firstSymbol === 'WILD' ? symbols.find(s => s !== 'WILD') : firstSymbol;
  
  for (let i = 1; i < symbols.length; i++) {
    if (symbols[i] === effectiveSymbol || symbols[i] === 'WILD') {
      matchCount++;
    } else {
      break; // must be consecutive from left
    }
  }
  
  // Need at least 3 matching symbols
  if (matchCount >= 3) {
    const multiplier = paytable[effectiveSymbol][matchCount];
    
    return {
      win: true,
      symbols: effectiveSymbol,
      count: matchCount,
      multiplier: multiplier
    };
  }
  
  return { win: false };
}

// 6. Free Spins Feature
async function triggerSlotFeature(session, triggerSpin, feature) {
  if (feature.type === "FREE_SPINS") {
    const freeSpinsSession = {
      id: generateFeatureId(),
      parentSpinId: triggerSpin.id,
      sessionId: session.id,
      playerId: session.playerId,
      gameId: session.gameId,
      type: "FREE_SPINS",
      spinsAwarded: feature.count,
      spinsRemaining: feature.count,
      totalWon: 0,
      spins: [],
      multiplier: feature.multiplier || 1, // some games have multipliers in free spins
      status: "ACTIVE"
    };
    
    await db.saveFeatureSession(freeSpinsSession);
    
    return {
      success: true,
      featureTriggered: true,
      feature: freeSpinsSession
    };
  }
  
  // Other features: PICK_BONUS, WHEEL_BONUS, etc.
}

// 7. Free spin execution
async function executeFre eSpin(featureId) {
  const feature = getFeatureSession(featureId);
  const game = getGameConfig(feature.gameId);
  
  // Free spin uses same bet config as trigger spin
  const triggerSpin = getSpin(feature.parentSpinId);
  const betConfig = triggerSpin.betConfig;
  
  // Generate spin (no cost)
  const spinResult = generateSlotResult(game, betConfig);
  
  // Calculate wins (with multiplier if applicable)
  const winResult = calculateSlotWins(game, spinResult, betConfig);
  const winAmount = winResult.totalWin * feature.multiplier;
  
  // Pay winnings
  if (winAmount > 0) {
    await db.updatePlayerBalance(feature.playerId, winAmount);
  }
  
  // Save free spin
  const spin = {
    id: generateSpinId(),
    featureId: featureId,
    result: spinResult,
    winAmount: winAmount,
    timestamp: Date.now()
  };
  
  feature.spins.push(spin);
  feature.totalWon += winAmount;
  feature.spinsRemaining--;
  
  // Check for retrigger
  if (spinResult.featureTriggered) {
    feature.spinsRemaining += getFreeSpinsCount(countScatters(spinResult.reels));
  }
  
  // Check if feature complete
  if (feature.spinsRemaining === 0) {
    feature.status = "COMPLETED";
    await db.updateFeatureSession(feature);
  }
  
  return {
    success: true,
    spin: spin,
    spinsRemaining: feature.spinsRemaining,
    totalFeatureWin: feature.totalWon
  };
}
```

---

# PHẦN 2: HỆ THỐNG HOA HỒNG (COMMISSION/AFFILIATE) {#phan-2}

## 1. CẤU TRÚC HỆ THỐNG HOA HỒNG

### 1.1. Mô hình hoa hồng đa cấp
```
┌─────────────────────────────────────────────────────────────┐
│                    SUPER AFFILIATE (Cấp 0)                  │
│                    Commission: 40%                           │
└─────────────────┬───────────────────────────────────────────┘
                  │
         ┌────────┴────────┐
         ▼                 ▼
┌──────────────────┐  ┌──────────────────┐
│  AGENT (Cấp 1)   │  │  AGENT (Cấp 1)   │
│  Commission: 30% │  │  Commission: 30% │
└────────┬─────────┘  └────────┬─────────┘
         │                     │
    ┌────┴────┐           ┌────┴────┐
    ▼         ▼           ▼         ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│SUB-    │ │SUB-    │ │SUB-    │ │SUB-    │
│AGENT   │ │AGENT   │ │AGENT   │ │AGENT   │
│(Cấp 2) │ │(Cấp 2) │ │(Cấp 2) │ │(Cấp 2) │
│20%     │ │20%     │ │20%     │ │20%     │
└────┬───┘ └────┬───┘ └────┬───┘ └────┬───┘
     │          │          │          │
     ▼          ▼          ▼          ▼
  Players    Players    Players    Players
```

### 1.2. Các cấp độ Affiliate
```javascript
const AFFILIATE_TIERS = {
  PLAYER: {
    level: 0,
    name: "Player",
    canRefer: true,
    commissionRate: 0.05, // 5% từ người mình giới thiệu trực tiếp
    requirements: {
      minReferrals: 0,
      minMonthlyRevenue: 0
    }
  },
  
  SUB_AGENT: {
    level: 1,
    name: "Sub-Agent",
    canRefer: true,
    commissionRate: 0.20, // 20%
    commissionLevels: {
      direct: 0.20, // F1
      indirect: 0.05 // F2
    },
    requirements: {
      minReferrals: 10,
      minMonthlyRevenue: 5000, // $5,000
      minActiveReferrals: 5 // ít nhất 5 người chơi active
    },
    benefits: [
      "Custom referral link",
      "Detailed reports",
      "Weekly payouts"
    ]
  },
  
  AGENT: {
    level: 2,
    name: "Agent",
    canRefer: true,
    commissionRate: 0.30, // 30%
    commissionLevels: {
      direct: 0.30, // F1
      indirect: 0.10, // F2
      indirect2: 0.05 // F3
    },
    requirements: {
      minReferrals: 50,
      minMonthlyRevenue: 25000, // $25,000
      minActiveReferrals: 20,
      minSubAgents: 3
    },
    benefits: [
      "All Sub-Agent benefits",
      "Dedicated account manager",
      "Marketing materials",
      "Higher commission rates",
      "Daily payouts"
    ]
  },
  
  SUPER_AFFILIATE: {
    level: 3,
    name: "Super Affiliate",
    canRefer: true,
    commissionRate: 0.40, // 40%
    commissionLevels: {
      direct: 0.40, // F1
      indirect: 0.15, // F2
      indirect2: 0.10, // F3
      indirect3: 0.05 // F4
    },
    requirements: {
      minReferrals: 200,
      minMonthlyRevenue: 100000, // $100,000
      minActiveReferrals: 100,
      minAgents: 5
    },
    benefits: [
      "All Agent benefits",
      "Negotiable commission rates",
      "CPA deals available",
      "Rev-share + hybrid models",
      "Instant payouts",
      "White-label opportunities"
    ]
  }
};
```

---

## 2. COMMISSION CALCULATION MODELS

### 2.1. Revenue Share Model (Mô hình chia doanh thu)
**Cách tính**: % của net revenue từ players giới thiệu

```javascript
async function calculateRevenueShareCommission(affiliateId, period) {
  // period = { startDate, endDate }
  
  const affiliate = getAffiliate(affiliateId);
  const tier = AFFILIATE_TIERS[affiliate.tier];
  
  // Lấy tất cả players do affiliate này giới thiệu (và downline)
  const referredPlayers = await getReferredPlayers(affiliateId, {
    includeDownline: true,
    maxLevels: tier.level + 1
  });
  
  let totalCommission = 0;
  const breakdown = [];
  
  // Tính cho từng player
  for (const playerRef of referredPlayers) {
    const player = playerRef.player;
    const relationLevel = playerRef.level; // 1 = direct, 2 = F2, etc.
    
    // Get player's gaming activity trong period
    const activity = await getPlayerActivity(player.id, period);
    
    // Net Gaming Revenue (NGR) = Total Bets - Total Wins - Bonuses
    const ngr = activity.totalWagered - activity.totalWon - activity.bonusesUsed;
    
    // Chỉ tính commission nếu NGR > 0 (player thua)
    if (ngr > 0) {
      // Commission rate dựa trên level
      const commissionRate = getCommissionRateByLevel(tier, relationLevel);
      const commission = ngr * commissionRate;
      
      totalCommission += commission;
      
      breakdown.push({
        playerId: player.id,
        username: player.username,
        level: relationLevel,
        totalWagered: activity.totalWagered,
        totalWon: activity.totalWon,
        ngr: ngr,
        commissionRate: commissionRate,
        commission: commission
      });
    }
  }
  
  return {
    affiliateId: affiliateId,
    period: period,
    totalCommission: totalCommission,
    breakdown: breakdown,
    stats: {
      totalPlayers: referredPlayers.length,
      activePlayers: breakdown.length,
      totalNGR: breakdown.reduce((sum, b) => sum + b.ngr, 0)
    }
  };
}

function getCommissionRateByLevel(tier, relationLevel) {
  switch(relationLevel) {
    case 1: return tier.commissionLevels.direct;
    case 2: return tier.commissionLevels.indirect || 0;
    case 3: return tier.commissionLevels.indirect2 || 0;
    case 4: return tier.commissionLevels.indirect3 || 0;
    default: return 0;
  }
}
```

### 2.2. CPA Model (Cost Per Acquisition)
**Cách tính**: Trả một khoản cố định cho mỗi người chơi mới deposit

```javascript
async function calculateCPACommission(affiliateId, period) {
  const affiliate = getAffiliate(affiliateId);
  
  // CPA rate dựa trên tier và thỏa thuận
  const cpaRates = {
    TIER_1: 200, // $200 per FTD (First Time Depositor)
    TIER_2: 150, // $150 if player deposits $100-$500
    TIER_3: 100  // $100 if player deposits $50-$100
  };
  
  // Lấy players mới đã deposit trong period
  const newDepositors = await getNewDepositors(affiliateId, period);
  
  let totalCommission = 0;
  const breakdown = [];
  
  for (const player of newDepositors) {
    const firstDeposit = player.firstDepositAmount;
    let cpaAmount = 0;
    
    // Phân tier dựa trên deposit amount
    if (firstDeposit >= 500) {
      cpaAmount = cpaRates.TIER_1;
    } else if (firstDeposit >= 100) {
      cpaAmount = cpaRates.TIER_2;
    } else if (firstDeposit >= 50) {
      cpaAmount = cpaRates.TIER_3;
    }
    
    // Điều kiện: Player phải active (wagered at least 3x deposit)
    if (player.totalWagered >= firstDeposit * 3) {
      totalCommission += cpaAmount;
      
      breakdown.push({
        playerId: player.id,
        username: player.username,
        firstDepositAmount: firstDeposit,
        totalWagered: player.totalWagered,
        cpaAmount: cpaAmount
      });
    }
  }
  
  return {
    affiliateId: affiliateId,
    period: period,
    model: "CPA",
    totalCommission: totalCommission,
    totalQualifiedPlayers: breakdown.length,
    breakdown: breakdown
  };
}
```

### 2.3. Hybrid Model (CPA + RevShare)
**Cách tính**: Kết hợp CPA lúc đầu + RevShare lâu dài

```javascript
async function calculateHybridCommission(affiliateId, period) {
  // Month 1: CPA
  // Month 2+: RevShare
  
  const cpaCommission = await calculateCPACommission(affiliateId, period);
  const revShareCommission = await calculateRevenueShareCommission(affiliateId, period);
  
  // Filter: Chỉ tính RevShare cho players sau tháng đầu
  const revShareFiltered = revShareCommission.breakdown.filter(b => {
    const player = getPlayer(b.playerId);
    const daysSinceRegistration = (Date.now() - player.registeredAt) / (1000 * 60 * 60 * 24);
    return daysSinceRegistration > 30;
  });
  
  const totalRevShare = revShareFiltered.reduce((sum, b) => sum + b.commission, 0);
  
  return {
    affiliateId: affiliateId,
    period: period,
    model: "HYBRID",
    cpa: {
      amount: cpaCommission.totalCommission,
      players: cpaCommission.totalQualifiedPlayers
    },
    revShare: {
      amount: totalRevShare,
      players: revShareFiltered.length
    },
    totalCommission: cpaCommission.totalCommission + totalRevShare
  };
}
```

---

## 3. REFERRAL SYSTEM IMPLEMENTATION

### 3.1. Generate Referral Code & Link
```javascript
function generateReferralCode(userId) {
  // Tạo unique code (6 chars alphanumeric)
  const code = generateUniqueCode(6); // e.g., "B2USQH"
  
  const referral = {
    userId: userId,
    code: code,
    link: `https://resortworldgenting.online/ref/${code}`,
    qrCode: generateQRCode(`https://resortworldgenting.online/ref/${code}`),
    createdAt: Date.now(),
    clicks: 0,
    registrations: 0,
    deposits: 0,
    totalRevenue: 0
  };
  
  db.saveReferralCode(referral);
  
  return referral;
}
```

### 3.2. Track Referral Click
```javascript
async function trackReferralClick(referralCode, metadata) {
  const referral = await db.getReferralByCode(referralCode);
  
  if (!referral) {
    return { success: false, error: "Invalid referral code" };
  }
  
  // Save click event
  const click = {
    referralCode: referralCode,
    affiliateId: referral.userId,
    ipAddress: metadata.ip,
    userAgent: metadata.userAgent,
    country: metadata.country,
    device: metadata.device,
    timestamp: Date.now()
  };
  
  await db.saveReferralClick(click);
  
  // Update counter
  await db.incrementReferralClicks(referralCode);
  
  // Set cookie for attribution (30 days)
  setCookie("ref_code", referralCode, { maxAge: 30 * 24 * 60 * 60 });
  
  return { success: true, referral: referral };
}
```

### 3.3. Attribute Registration
```javascript
async function registerWithReferral(userData, referralCode) {
  // Tạo tài khoản mới
  const user = await createUser(userData);
  
  // Link to referrer
  if (referralCode) {
    const referral = await db.getReferralByCode(referralCode);
    
    if (referral) {
      // Tạo relationship
      const relationship = {
        referrerId: referral.userId,
        referredId: user.id,
        referralCode: referralCode,
        level: 1, // direct referral
        status: "PENDING", // becomes ACTIVE after first deposit
        registeredAt: Date.now(),
        firstDepositAt: null
      };
      
      await db.saveReferralRelationship(relationship);
      
      // Update referral stats
      await db.incrementReferralRegistrations(referralCode);
      
      // Build upline chain (for multi-level)
      await buildUplineChain(user.id, referral.userId);
    }
  }
  
  return user;
}
```

### 3.4. Build Upline Chain (Multi-Level)
```javascript
async function buildUplineChain(newUserId, directReferrerId) {
  // Lấy upline của referrer
  const referrerChain = await db.getUplineChain(directReferrerId);
  
  const chain = [
    {
      userId: newUserId,
      upliner: directReferrerId,
      level: 1
    }
  ];
  
  // Add indirect uplines
  let currentLevel = 2;
  for (const upline of referrerChain) {
    if (currentLevel > 4) break; // Max 4 levels
    
    chain.push({
      userId: newUserId,
      upliner: upline.userId,
      level: currentLevel
    });
    
    currentLevel++;
  }
  
  // Save chain
  await db.saveUplineChain(newUserId, chain);
  
  return chain;
}
```

### 3.5. Activate Referral (First Deposit)
```javascript
async function onFirstDeposit(userId, depositAmount) {
  // Update referral relationship status
  const relationships = await db.getReferralRelationships(userId, { status: "PENDING" });
  
  for (const rel of relationships) {
    rel.status = "ACTIVE";
    rel.firstDepositAt = Date.now();
    rel.firstDepositAmount = depositAmount;
    
    await db.updateReferralRelationship(rel);
    
    // Notify referrer
    await sendNotification(rel.referrerId, {
      type: "REFERRAL_DEPOSITED",
      message: `Your referral ${getUsernameM asked(userId)} made their first deposit!`,
      amount: depositAmount
    });
    
    // Award welcome bonus to referrer (nếu có)
    await awardReferralBonus(rel.referrerId, depositAmount);
  }
}
```

---

## 4. COMMISSION PAYOUT SYSTEM

### 4.1. Calculate Commission Period
```javascript
// Chạy cuối mỗi tuần hoặc tháng
async function calculateCommissionPayouts(period) {
  // period = "WEEKLY" | "MONTHLY"
  
  const startDate = getPeriodStart(period);
  const endDate = getPeriodEnd(period);
  
  // Lấy tất cả affiliates
  const affiliates = await db.getActiveAffiliates();
  
  const payouts = [];
  
  for (const affiliate of affiliates) {
    // Calculate commission
    const commission = await calculateRevenueShareCommission(
      affiliate.id, 
      { startDate, endDate }
    );
    
    if (commission.totalCommission > 0) {
      // Trừ fees (nếu có)
      const fees = commission.totalCommission * 0.02; // 2% processing fee
      const netCommission = commission.totalCommission - fees;
      
      // Minimum payout threshold
      if (netCommission >= affiliate.minPayoutThreshold || 50) { // default $50
        const payout = {
          id: generatePayoutId(),
          affiliateId: affiliate.id,
          period: { startDate, endDate },
          grossCommission: commission.totalCommission,
          fees: fees,
          netCommission: netCommission,
          status: "PENDING",
          createdAt: Date.now(),
          paidAt: null,
          method: affiliate.payoutMethod, // BANK_TRANSFER, CRYPTO, etc.
          details: commission.breakdown
        };
        
        await db.saveCommissionPayout(payout);
        payouts.push(payout);
        
        // Send notification
        await sendNotification(affiliate.id, {
          type: "COMMISSION_READY",
          amount: netCommission,
          period: period
        });
      } else {
        // Carry over to next period
        await db.addCarryOverCommission(affiliate.id, netCommission);
      }
    }
  }
  
  return payouts;
}
```

### 4.2. Process Payout
```javascript
async function processCommissionPayout(payoutId) {
  const payout = await db.getPayout(payoutId);
  const affiliate = await getAffiliate(payout.affiliateId);
  
  // Check affiliate payout details
  if (!affiliate.payoutDetails) {
    return { success: false, error: "Payout details not configured" };
  }
  
  let paymentResult;
  
  switch(affiliate.payoutMethod) {
    case "BANK_TRANSFER":
      paymentResult = await processBankTransfer({
        amount: payout.netCommission,
        bankName: affiliate.payoutDetails.bankName,
        accountNumber: affiliate.payoutDetails.accountNumber,
        accountName: affiliate.payoutDetails.accountName
      });
      break;
      
    case "CRYPTO":
      paymentResult = await processCryptoPayment({
        amount: payout.netCommission,
        currency: affiliate.payoutDetails.cryptoCurrency, // BTC, USDT, etc.
        address: affiliate.payoutDetails.walletAddress
      });
      break;
      
    case "EWALLET":
      paymentResult = await processEWalletPayment({
        amount: payout.netCommission,
        provider: affiliate.payoutDetails.provider, // Skrill, Neteller, etc.
        account: affiliate.payoutDetails.account
      });
      break;
      
    default:
      return { success: false, error: "Invalid payout method" };
  }
  
  if (paymentResult.success) {
    // Update payout status
    payout.status = "PAID";
    payout.paidAt = Date.now();
    payout.transactionId = paymentResult.transactionId;
    
    await db.updatePayout(payout);
    
    // Send confirmation
    await sendNotification(affiliate.id, {
      type: "COMMISSION_PAID",
      amount: payout.netCommission,
      method: affiliate.payoutMethod,
      transactionId: paymentResult.transactionId
    });
    
    return { success: true, payout: payout };
  } else {
    payout.status = "FAILED";
    payout.failureReason = paymentResult.error;
    await db.updatePayout(payout);
    
    return { success: false, error: paymentResult.error };
  }
}
```

---

## 5. AFFILIATE DASHBOARD (CHO NGƯỜI GIỚI THIỆU)

### 5.1. Dashboard Overview
```javascript
async function getAffiliateDashboard(affiliateId) {
  const affiliate = await getAffiliate(affiliateId);
  
  // Stats tổng quan
  const stats = {
    // Lifetime stats
    totalClicks: affiliate.totalClicks,
    totalRegistrations: affiliate.totalRegistrations,
    totalDepositors: affiliate.totalDepositors,
    totalRevenue: affiliate.totalRevenue,
    totalCommissionEarned: affiliate.totalCommissionEarned,
    
    // Current month
    thisMonth: {
      clicks: await countClicks(affiliateId, "THIS_MONTH"),
      registrations: await countRegistrations(affiliateId, "THIS_MONTH"),
      depositors: await countDepositors(affiliateId, "THIS_MONTH"),
      revenue: await sumRevenue(affiliateId, "THIS_MONTH"),
      commission: await sumCommission(affiliateId, "THIS_MONTH")
    },
    
    // Conversion rates
    conversionRates: {
      clickToRegister: (affiliate.totalRegistrations / affiliate.totalClicks * 100).toFixed(2) + "%",
      registerToDeposit: (affiliate.totalDepositors / affiliate.totalRegistrations * 100).toFixed(2) + "%",
      overallConversion: (affiliate.totalDepositors / affiliate.totalClicks * 100).toFixed(2) + "%"
    },
    
    // Current tier
    tier: affiliate.tier,
    tierProgress: await calculateTierProgress(affiliate),
    
    // Pending commission
    pendingCommission: await getPendingCommission(affiliateId),
    nextPayoutDate: getNextPayoutDate()
  };
  
  return stats;
}
```

### 5.2. Referral List & Stats
```javascript
async function getAffiliateReferrals(affiliateId, filters) {
  const referrals = await db.getReferrals(affiliateId, filters);
  
  const enrichedReferrals = await Promise.all(
    referrals.map(async (ref) => {
      const player = await getPlayer(ref.referredId);
      const activity = await getPlayerActivity(ref.referredId, { period: "ALL_TIME" });
      
      return {
        playerId: ref.referredId,
        username: player.username,
        registeredAt: ref.registeredAt,
        firstDepositAt: ref.firstDepositAt,
        level: ref.level, // 1 = direct, 2+ = indirect
        status: ref.status, // ACTIVE, INACTIVE, BANNED
        
        // Activity stats
        totalDeposits: activity.totalDeposits,
        totalWagered: activity.totalWagered,
        totalWon: activity.totalWon,
        ngr: activity.ngr,
        
        // Commission generated
        commissionGenerated: await getCommissionFromPlayer(affiliateId, ref.referredId),
        lastActiveAt: player.lastLoginAt
      };
    })
  );
  
  return enrichedReferrals;
}
```

---

# PHẦN 3: ADMIN QUẢN LÝ {#phan-3}

## 1. ADMIN DASHBOARD - TỔNG QUAN

### 1.1. Main Dashboard
```javascript
async function getAdminDashboard(adminId, period = "TODAY") {
  return {
    // Real-time metrics
    realtime: {
      playersOnline: await countOnlinePlayers(),
      activeGames: await countActiveGames(),
      betsPerMinute: await getBetsPerMinute(),
      currentRevenue: await getCurrentRevenue()
    },
    
    // Financial metrics
    financial: {
      totalDeposits: await sumDeposits(period),
      totalWithdrawals: await sumWithdrawals(period),
      netCashFlow: await calculateNetCashFlow(period),
      ggr: await calculateGGR(period), // Gross Gaming Revenue
      ngr: await calculateNGR(period), // Net Gaming Revenue
      bonusCost: await sumBonusCosts(period),
      profit: await calculateProfit(period)
    },
    
    // Player metrics
    players: {
      totalPlayers: await countPlayers(),
      newPlayers: await countNewPlayers(period),
      activePlayers: await countActivePlayers(period),
      depositingPlayers: await countDepositingPlayers(period),
      highRollers: await getHighRollers(10), // top 10
      vipPlayers: await countVIPPlayers()
    },
    
    // Game metrics
    games: {
      totalBets: await countBets(period),
      totalWagered: await sumWagered(period),
      totalWon: await sumWinnings(period),
      houseEdge: await calculateHouseEdge(period),
      popularGames: await getPopularGames(period, 10),
      biggestWins: await getBiggestWins(period, 10)
    },
    
    // Pending tasks
    pendingTasks: {
      kycPending: await countPendingKYC(),
      withdrawalsPending: await countPendingWithdrawals(),
      supportTickets: await countOpenTickets(),
      bonusApprovals: await countPendingBonuses()
    },
    
    // Alerts
    alerts: await getSystemAlerts()
  };
}
```

### 1.2. Financial Charts & Reports
```javascript
async function getFinancialCharts(period) {
  return {
    // Revenue trend (line chart)
    revenueTrend: await getRevenueTrend(period),
    
    // Deposits vs Withdrawals (bar chart)
    cashFlowChart: await getCashFlowChart(period),
    
    // Game type breakdown (pie chart)
    revenueByGameType: await getRevenueByGameType(period),
    
    // Provider performance (bar chart)
    providerPerformance: await getProviderPerformance(period),
    
    // Player segments (pie chart)
    playerSegments: {
      whales: await getSegmentRevenue("WHALE", period), // >$10k/month
      highRollers: await getSegmentRevenue("HIGH_ROLLER", period), // $1k-$10k
      regulars: await getSegmentRevenue("REGULAR", period), // $100-$1k
      casuals: await getSegmentRevenue("CASUAL", period) // <$100
    }
  };
}
```

---

## 2. PLAYER MANAGEMENT (QUẢN LÝ NGƯỜI CHƠI)

### 2.1. Player List & Search
```javascript
async function searchPlayers(filters) {
  // filters = {
  //   search: "email or username",
  //   vipLevel: "GOLD",
  //   status: "ACTIVE",
  //   country: "VN",
  //   registeredFrom: date,
  //   registeredTo: date,
  //   hasKYC: true,
  //   hasDeposited: true,
  //   minBalance: 100,
  //   maxBalance: 10000
  // }
  
  const players = await db.searchPlayers(filters, {
    limit: 50,
    offset: 0,
    orderBy: "lastLoginAt DESC"
  });
  
  return players.map(p => ({
    id: p.id,
    username: p.username,
    email: p.email,
    vipLevel: p.vipLevel,
    balance: p.balance,
    totalDeposited: p.totalDeposited,
    totalWithdrawn: p.totalWithdrawn,
    ngr: p.ngrLifetime, // lifetime net gaming revenue
    kycStatus: p.kycStatus,
    status: p.status,
    registeredAt: p.registeredAt,
    lastLoginAt: p.lastLoginAt,
    country: p.country
  }));
}
```

### 2.2. Player Detail View
```javascript
async function getPlayerDetails(playerId) {
  const player = await getPlayer(playerId);
  
  return {
    // Basic info
    profile: {
      id: player.id,
      username: player.username,
      email: player.email,
      fullName: player.fullName,
      phone: player.phone,
      dateOfBirth: player.dateOfBirth,
      country: player.country,
      registeredAt: player.registeredAt,
      lastLoginAt: player.lastLoginAt,
      ipAddresses: await getPlayerIPs(playerId)
    },
    
    // Account status
    status: {
      accountStatus: player.status, // ACTIVE, SUSPENDED, BANNED
      kycStatus: player.kycStatus, // PENDING, VERIFIED, REJECTED
      emailVerified: player.emailVerified,
      phoneVerified: player.phoneVerified,
      twoFactorEnabled: player.twoFactorEnabled
    },
    
    // Financial info
    financial: {
      currentBalance: player.balance,
      bonusBalance: player.bonusBalance,
      totalDeposited: player.totalDeposited,
      totalWithdrawn: player.totalWithdrawn,
      netDeposit: player.totalDeposited - player.totalWithdrawn,
      
      totalWagered: player.totalWagered,
      totalWon: player.totalWon,
      ngrLifetime: player.ngrLifetime,
      
      pendingWithdrawals: await getPendingWithdrawals(playerId),
      
      // Recent transactions
      recentDeposits: await getRecentDeposits(playerId, 5),
      recentWithdrawals: await getRecentWithdrawals(playerId, 5)
    },
    
    // Gaming activity
    gaming: {
      totalBets: player.totalBets,
      totalSessions: player.totalSessions,
      avgSessionDuration: player.avgSessionDuration,
      favoriteGames: await getFavoriteGames(playerId),
      biggestWin: player.biggestWin,
      lastPlayed: player.lastPlayedAt
    },
    
    // VIP & Rewards
    vip: {
      currentTier: player.vipLevel,
      loyaltyPoints: player.loyaltyPoints,
      pointsToNextTier: await getPointsToNextTier(playerId),
      cashbackEarned: player.cashbackEarned,
      bonusesReceived: await countBonusesReceived(playerId)
    },
    
    // Referrals
    referrals: {
      referredBy: await getReferrer(playerId),
      referredPlayers: await getReferredPlayers(playerId),
      commissionGenerated: await getCommissionGenerated(playerId)
    },
    
    // Risk & Compliance
    risk: {
      riskScore: await calculateRiskScore(playerId),
      flags: await getRiskFlags(playerId), // ["MULTIPLE_ACCOUNTS", "BONUS_ABUSE", etc.]
      selfExcluded: player.selfExcluded,
      depositLimits: player.depositLimits,
      lossLimits: player.lossLimits,
      sessionLimits: player.sessionLimits
    },
    
    // Support history
    support: {
      totalTickets: await countTickets(playerId),
      openTickets: await countOpenTickets(playerId),
      lastContact: await getLastContact(playerId),
      notes: await getAdminNotes(playerId)
    }
  };
}
```

### 2.3. Player Actions (Admin Controls)
```javascript
// A. Adjust Balance
async function adminAdjustBalance(adminId, playerId, adjustment) {
  // adjustment = {
  //   amount: 100, // positive = add, negative = deduct
  //   reason: "Goodwill bonus",
  //   type: "BONUS" | "CORRECTION" | "REFUND"
  // }
  
  const player = await getPlayer(playerId);
  const admin = await getAdmin(adminId);
  
  // Validate
  if (adjustment.amount < 0 && player.balance + adjustment.amount < 0) {
    return { success: false, error: "Insufficient balance" };
  }
  
  // Execute
  const transaction = await db.transaction(async () => {
    // Update balance
    await db.updatePlayerBalance(playerId, adjustment.amount);
    
    // Log transaction
    const txn = {
      id: generateTransactionId(),
      playerId: playerId,
      type: "ADMIN_ADJUSTMENT",
      subType: adjustment.type,
      amount: adjustment.amount,
      balanceBefore: player.balance,
      balanceAfter: player.balance + adjustment.amount,
      adminId: adminId,
      adminUsername: admin.username,
      reason: adjustment.reason,
      timestamp: Date.now()
    };
    
    await db.saveTransaction(txn);
    
    // Log admin action
    await logAdminAction({
      adminId: adminId,
      action: "BALANCE_ADJUSTMENT",
      targetType: "PLAYER",
      targetId: playerId,
      details: adjustment,
      ipAddress: admin.currentIP,
      timestamp: Date.now()
    });
    
    return txn;
  });
  
  // Notify player
  await sendNotification(playerId, {
    type: "BALANCE_UPDATED",
    amount: adjustment.amount,
    reason: adjustment.reason
  });
  
  return { success: true, transaction: transaction };
}

// B. Change VIP Level
async function adminChangeVIPLevel(adminId, playerId, newVIPLevel, reason) {
  const player = await getPlayer(playerId);
  const oldVIPLevel = player.vipLevel;
  
  await db.updatePlayerVIPLevel(playerId, newVIPLevel);
  
  // Log
  await logAdminAction({
    adminId: adminId,
    action: "VIP_LEVEL_CHANGE",
    targetType: "PLAYER",
    targetId: playerId,
    details: {
      from: oldVIPLevel,
      to: newVIPLevel,
      reason: reason
    }
  });
  
  // Notify
  await sendNotification(playerId, {
    type: "VIP_LEVEL_UPDATED",
    newLevel: newVIPLevel,
    benefits: getVIPBenefits(newVIPLevel)
  });
  
  return { success: true };
}

// C. Suspend/Ban Account
async function adminSuspendAccount(adminId, playerId, suspension) {
  // suspension = {
  //   duration: "PERMANENT" | number (days),
  //   reason: "Fraud detected",
  //   note: "Admin notes"
  // }
  
  const expiresAt = suspension.duration === "PERMANENT" 
    ? null 
    : Date.now() + (suspension.duration * 24 * 60 * 60 * 1000);
  
  await db.updatePlayerStatus(playerId, "SUSPENDED", {
    suspendedBy: adminId,
    suspendedAt: Date.now(),
    expiresAt: expiresAt,
    reason: suspension.reason,
    note: suspension.note
  });
  
  // Kick player if online
  await kickPlayer(playerId);
  
  // Log
  await logAdminAction({
    adminId: adminId,
    action: "ACCOUNT_SUSPENDED",
    targetType: "PLAYER",
    targetId: playerId,
    details: suspension
  });
  
  // Email notification
  await sendEmail(playerId, {
    template: "ACCOUNT_SUSPENDED",
    data: {
      reason: suspension.reason,
      duration: suspension.duration,
      expiresAt: expiresAt
    }
  });
  
  return { success: true };
}

// D. Award Bonus
async function adminAwardBonus(adminId, playerId, bonus) {
  // bonus = {
  //   type: "DEPOSIT_BONUS" | "FREE_SPINS" | "CASHBACK",
  //   amount: 100,
  //   wageringRequirement: 30, // 30x
  //   expiryDays: 7,
  //   reason: "Compensation for downtime"
  // }
  
  const bonusRecord = {
    id: generateBonusId(),
    playerId: playerId,
    type: bonus.type,
    amount: bonus.amount,
    wageringRequired: bonus.amount * bonus.wageringRequirement,
    wageringCompleted: 0,
    status: "ACTIVE",
    awardedBy: adminId,
    awardedAt: Date.now(),
    expiresAt: Date.now() + (bonus.expiryDays * 24 * 60 * 60 * 1000),
    reason: bonus.reason
  };
  
  await db.saveBonus(bonusRecord);
  
  // Credit bonus balance
  if (bonus.type !== "FREE_SPINS") {
    await db.updatePlayerBonusBalance(playerId, bonus.amount);
  }
  
  // Notify
  await sendNotification(playerId, {
    type: "BONUS_AWARDED",
    bonus: bonusRecord
  });
  
  return { success: true, bonus: bonusRecord };
}

// E. Force Verify KYC
async function adminVerifyKYC(adminId, playerId, verification) {
  // verification = {
  //   status: "APPROVED" | "REJECTED",
  //   note: "Documents verified",
  //   verifiedLevel: 2
  // }
  
  await db.updatePlayerKYC(playerId, {
    status: verification.status,
    verifiedLevel: verification.verifiedLevel,
    verifiedBy: adminId,
    verifiedAt: Date.now(),
    note: verification.note
  });
  
  // Log
  await logAdminAction({
    adminId: adminId,
    action: "KYC_VERIFICATION",
    targetType: "PLAYER",
    targetId: playerId,
    details: verification
  });
  
  // Notify
  await sendNotification(playerId, {
    type: "KYC_UPDATED",
    status: verification.status,
    level: verification.verifiedLevel
  });
  
  return { success: true };
}

// F. Add Admin Note
async function adminAddNote(adminId, playerId, note) {
  const noteRecord = {
    id: generateNoteId(),
    playerId: playerId,
    adminId: adminId,
    content: note.content,
    type: note.type, // "INFO", "WARNING", "RISK"
    createdAt: Date.now()
  };
  
  await db.saveAdminNote(noteRecord);
  
  return { success: true, note: noteRecord };
}
```

---

## 3. TRANSACTION MANAGEMENT (QUẢN LÝ GIAO DỊCH)

### 3.1. Withdrawal Approval Process
```javascript
async function getWithdrawalQueue(filters) {
  // filters = {
  //   status: "PENDING",
  //   minAmount: 100,
  //   maxAmount: 10000,
  //   priority: "HIGH" | "NORMAL" | "LOW"
  // }
  
  const withdrawals = await db.getWithdrawals(filters, {
    orderBy: "createdAt ASC" // FIFO
  });
  
  // Enrich with player info and risk assessment
  return await Promise.all(withdrawals.map(async (wd) => {
    const player = await getPlayer(wd.playerId);
    const riskAssessment = await assessWithdrawalRisk(wd);
    
    return {
      ...wd,
      playerInfo: {
        username: player.username,
        vipLevel: player.vipLevel,
        kycStatus: player.kycStatus
      },
      riskAssessment: riskAssessment,
      autoApprovalEligible: riskAssessment.score < 30 && wd.amount < 1000
    };
  }));
}

async function assessWithdrawalRisk(withdrawal) {
  const player = await getPlayer(withdrawal.playerId);
  
  let riskScore = 0;
  const flags = [];
  
  // Factor 1: KYC status
  if (!player.kycVerified) {
    riskScore += 30;
    flags.push("KYC_NOT_VERIFIED");
  }
  
  // Factor 2: Wagering requirement
  const wageringRatio = player.totalWagered / player.totalDeposited;
  if (wageringRatio < 1.0) {
    riskScore += 25;
    flags.push("LOW_WAGERING_RATIO");
  }
  
  // Factor 3: Multiple accounts check
  const similarAccounts = await findSimilarAccounts(player.id);
  if (similarAccounts.length > 0) {
    riskScore += 40;
    flags.push("POSSIBLE_MULTI_ACCOUNT");
  }
  
  // Factor 4: First withdrawal
  const previousWithdrawals = await countWithdrawals(player.id);
  if (previousWithdrawals === 0) {
    riskScore += 10;
    flags.push("FIRST_WITHDRAWAL");
  }
  
  // Factor 5: Bonus abuse check
  if (await checkBonusAbuse(player.id)) {
    riskScore += 30;
    flags.push("BONUS_ABUSE_SUSPECTED");
  }
  
  // Factor 6: Large amount
  if (withdrawal.amount > 5000) {
    riskScore += 15;
    flags.push("LARGE_AMOUNT");
  }
  
  return {
    score: riskScore,
    level: riskScore < 30 ? "LOW" : riskScore < 60 ? "MEDIUM" : "HIGH",
    flags: flags
  };
}

async function adminApproveWithdrawal(adminId, withdrawalId, approval) {
  const withdrawal = await getWithdrawal(withdrawalId);
  const player = await getPlayer(withdrawal.playerId);
  
  if (approval.approved) {
    // Approve
    withdrawal.status = "APPROVED";
    withdrawal.approvedBy = adminId;
    withdrawal.approvedAt = Date.now();
    withdrawal.note = approval.note;
    
    await db.updateWithdrawal(withdrawal);
    
    // Trigger payment processing
    await processWithdrawalPayment(withdrawalId);
    
    // Notify player
    await sendNotification(withdrawal.playerId, {
      type: "WITHDRAWAL_APPROVED",
      amount: withdrawal.amount,
      method: withdrawal.method
    });
    
  } else {
    // Reject
    withdrawal.status = "REJECTED";
    withdrawal.rejectedBy = adminId;
    withdrawal.rejectedAt = Date.now();
    withdrawal.rejectionReason = approval.reason;
    
    await db.updateWithdrawal(withdrawal);
    
    // Refund to player balance
    await db.updatePlayerBalance(player.id, withdrawal.amount);
    
    // Notify player
    await sendNotification(withdrawal.playerId, {
      type: "WITHDRAWAL_REJECTED",
      amount: withdrawal.amount,
      reason: approval.reason
    });
  }
  
  // Log admin action
  await logAdminAction({
    adminId: adminId,
    action: "WITHDRAWAL_DECISION",
    targetType: "WITHDRAWAL",
    targetId: withdrawalId,
    details: approval
  });
  
  return { success: true, withdrawal: withdrawal };
}
```

### 3.2. Transaction History & Reports
```javascript
async function getTransactionReport(filters) {
  // filters = {
  //   type: "DEPOSIT" | "WITHDRAWAL" | "BET" | "WIN",
  //   playerId: xxx,
  //   dateFrom: date,
  //   dateTo: date,
  //   minAmount: 100,
  //   status: "COMPLETED"
  // }
  
  const transactions = await db.getTransactions(filters, {
    limit: 1000,
    orderBy: "timestamp DESC"
  });
  
  const summary = {
    totalCount: transactions.length,
    totalAmount: transactions.reduce((sum, txn) => sum + txn.amount, 0),
    byType: groupBy(transactions, 'type'),
    byStatus: groupBy(transactions, 'status'),
    byMethod: groupBy(transactions, 'method')
  };
  
  return {
    transactions: transactions,
    summary: summary
  };
}
```

---

## 4. GAME MANAGEMENT (QUẢN LÝ GAME)

### 4.1. Game Library Management
```javascript
async function getGameLibrary() {
  const games = await db.getAllGames();
  
  return games.map(game => ({
    id: game.id,
    name: game.name,
    provider: game.provider,
    type: game.type, // LIVE_CASINO, SLOT, TABLE_GAME
    status: game.status, // ACTIVE, INACTIVE, MAINTENANCE
    rtp: game.rtp,
    popularity: game.playCount,
    revenue: game.totalRevenue,
    lastPlayed: game.lastPlayedAt
  }));
}

async function adminToggleGame(adminId, gameId, enabled) {
  await db.updateGameStatus(gameId, enabled ? "ACTIVE" : "INACTIVE");
  
  await logAdminAction({
    adminId: adminId,
    action: "GAME_STATUS_CHANGE",
    targetType: "GAME",
    targetId: gameId,
    details: { enabled: enabled }
  });
  
  return { success: true };
}

async function adminUpdateGameLimits(adminId, gameId, limits) {
  // limits = {
  //   minBet: 1,
  //   maxBet: 1000,
  //   maxWin: 100000
  // }
  
  await db.updateGameLimits(gameId, limits);
  
  await logAdminAction({
    adminId: adminId,
    action: "GAME_LIMITS_UPDATE",
    targetType: "GAME",
    targetId: gameId,
    details: limits
  });
  
  return { success: true };
}
```

### 4.2. Live Table Management
```javascript
async function getLiveTables() {
  const tables = await db.getLiveTables();
  
  return tables.map(table => ({
    id: table.id,
    game: table.gameName, // Roulette, Baccarat, etc.
    dealerName: table.dealerName,
    minBet: table.minBet,
    maxBet: table.maxBet,
    tier: table.tier, // Standard, Premium, Gold, Platinum
    currentPlayers: table.currentPlayers,
    maxPlayers: table.maxPlayers,
    status: table.status, // OPEN, CLOSED, MAINTENANCE
    currentRound: table.currentRound,
    totalRevenue: table.totalRevenue
  }));
}

async function adminOpenCloseTable(adminId, tableId, action) {
  // action = "OPEN" | "CLOSE" | "MAINTENANCE"
  
  const table = await getTable(tableId);
  
  if (action === "CLOSE" || action === "MAINTENANCE") {
    // Kick all players
    await notifyTablePlayers(tableId, {
      type: "TABLE_CLOSING",
      message: `Table ${table.name} is closing. Please finish your bets.`
    });
    
    // Wait for current round to finish
    await waitForRoundComplete(tableId);
  }
  
  await db.updateTableStatus(tableId, action);
  
  await logAdminAction({
    adminId: adminId,
    action: "TABLE_STATUS_CHANGE",
    targetType: "TABLE",
    targetId: tableId,
    details: { action: action }
  });
  
  return { success: true };
}
```

---

## 5. BONUS & PROMOTION MANAGEMENT

### 5.1. Create Promotion
```javascript
async function adminCreatePromotion(adminId, promotion) {
  // promotion = {
  //   name: "Welcome Bonus 100%",
  //   type: "DEPOSIT_MATCH",
  //   percentage: 100,
  //   maxBonus: 500,
  //   wageringRequirement: 30,
  //   minDeposit: 20,
  //   eligibility: {
  //     newPlayersOnly: true,
  //     vipLevels: ["MEMBER", "BRONZE"],
  //     countries: ["VN", "TH", "ID"]
  //   },
  //   validFrom: Date.now(),
  //   validUntil: Date.now() + (30 * 24 * 60 * 60 * 1000),
  //   promoCode: "WELCOME100",
  //   termsAndConditions: "..."
  // }
  
  const promoRecord = {
    id: generatePromoId(),
    ...promotion,
    createdBy: adminId,
    createdAt: Date.now(),
    status: "ACTIVE",
    claimCount: 0,
    totalAwarded: 0
  };
  
  await db.savePromotion(promoRecord);
  
  await logAdminAction({
    adminId: adminId,
    action: "PROMOTION_CREATED",
    targetType: "PROMOTION",
    targetId: promoRecord.id,
    details: promotion
  });
  
  return { success: true, promotion: promoRecord };
}
```

### 5.2. Monitor Bonus Usage
```javascript
async function getBonusReport(filters) {
  const bonuses = await db.getBonuses(filters);
  
  return {
    total: bonuses.length,
    totalAwarded: bonuses.reduce((sum, b) => sum + b.amount, 0),
    totalWagered: bonuses.reduce((sum, b) => sum + b.wageringCompleted, 0),
    completed: bonuses.filter(b => b.status === "COMPLETED").length,
    expired: bonuses.filter(b => b.status === "EXPIRED").length,
    active: bonuses.filter(b => b.status === "ACTIVE").length,
    
    byType: groupBy(bonuses, 'type'),
    topUsers: await getTopBonusUsers(10)
  };
}
```

---

## 6. REPORTS & ANALYTICS

### 6.1. Financial Reports
```javascript
async function generateFinancialReport(period) {
  return {
    period: period,
    
    revenue: {
      deposits: await sumDeposits(period),
      withdrawals: await sumWithdrawals(period),
      netCashFlow: await calculateNetCashFlow(period),
      
      ggr: await calculateGGR(period),
      ngr: await calculateNGR(period),
      
      bonusCosts: await sumBonusCosts(period),
      commissionPaid: await sumCommissionPaid(period),
      operatingCosts: await getOperatingCosts(period),
      
      netProfit: await calculateNetProfit(period)
    },
    
    breakdown: {
      byGame: await getRevenueByGame(period),
      byProvider: await getRevenueByProvider(period),
      byCountry: await getRevenueByCountry(period),
      byVIPLevel: await getRevenueByVIPLevel(period)
    },
    
    projections: {
      nextMonth: await projectRevenue("NEXT_MONTH"),
      nextQuarter: await projectRevenue("NEXT_QUARTER")
    }
  };
}
```

### 6.2. Player Analytics
```javascript
async function generatePlayerReport(period) {
  return {
    acquisition: {
      newPlayers: await countNewPlayers(period),
      acquisitionCost: await calculateCAC(period),
      conversionRate: await calculateConversionRate(period),
      topSources: await getTopAcquisitionSources(period)
    },
    
    retention: {
      activeUsers: await countActiveUsers(period),
      retentionRate: await calculateRetentionRate(period),
      churnRate: await calculateChurnRate(period),
      avgLifespan: await calculateAvgLifespan()
    },
    
    engagement: {
      avgSessionDuration: await calculateAvgSessionDuration(period),
      avgSessions PerUser: await calculateAvgSessionsPerUser(period),
      avgBetsPerSession: await calculateAvgBetsPerSession(period)
    },
    
    monetization: {
      arpu: await calculateARPU(period), // Average Revenue Per User
      arppu: await calculateARPPU(period), // Average Revenue Per Paying User
      ltv: await calculateLTV(), // Lifetime Value
      avgDepositSize: await calculateAvgDepositSize(period),
      depositFrequency: await calculateDepositFrequency(period)
    }
  };
}
```

---

## 7. ADMIN ROLES & PERMISSIONS

### 7.1. Admin Roles
```javascript
const ADMIN_ROLES = {
  SUPER_ADMIN: {
    level: 4,
    permissions: ["*"], // All permissions
    description: "Full system access"
  },
  
  MANAGER: {
    level: 3,
    permissions: [
      "players.view", "players.edit", "players.suspend",
      "transactions.view", "transactions.approve",
      "games.view", "games.edit",
      "promotions.view", "promotions.create", "promotions.edit",
      "reports.view", "reports.export",
      "support.view", "support.respond"
    ],
    description: "Management level access"
  },
  
  FINANCE_ADMIN: {
    level: 2,
    permissions: [
      "players.view",
      "transactions.view", "transactions.approve", "transactions.refund",
      "reports.view", "reports.export"
    ],
    description: "Financial operations"
  },
  
  SUPPORT_AGENT: {
    level: 1,
    permissions: [
      "players.view",
      "transactions.view",
      "support.view", "support.respond",
      "tickets.view", "tickets.respond"
    ],
    description: "Customer support"
  }
};
```

---

**DOCUMENT VERSION**: 1.0  
**Created**: 2026-08-19  
**Total Pages**: ~40+

*File này chứa logic game hoàn chỉnh, hệ thống hoa hồng chi tiết, và tất cả chức năng admin cần thiết cho một casino platform.*
