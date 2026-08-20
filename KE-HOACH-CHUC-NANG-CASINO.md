# KẾ HOẠCH CHỨC NĂNG - HỆ THỐNG CASINO TRỰC TUYẾN
# RESORT WORLD GENTING GAMING PLATFORM

**Loại hình**: Casino trực tuyến (Live Casino + Slots + Sports Betting)  
**Mô hình kinh doanh**: B2C Gaming Platform  
**Giấy phép**: Cần giấy phép cá cược hợp pháp (Curacao, Malta, Philippines, etc.)

---

## MỤC LỤC
1. [Authentication & User Management](#module-1)
2. [Wallet & Payment System](#module-2)
3. [Live Casino Games](#module-3)
4. [Slot Games](#module-4)
5. [Sports Betting (Optional)](#module-5)
6. [Lottery & Lucky Numbers](#module-6)
7. [VIP & Rewards System](#module-7)
8. [Game Providers Integration](#module-8)
9. [Admin & BackOffice](#module-9)
10. [Compliance & Security](#module-10)

---

## MODULE 1: AUTHENTICATION & USER MANAGEMENT

### 1.1. User Registration
**Chức năng**:
- Form đăng ký với validation
- Xác thực email/SMS OTP
- KYC (Know Your Customer) - bắt buộc với casino
- Age verification (18+/21+ tùy luật)
- Terms & conditions acceptance
- Responsible gaming acknowledgment

**Fields cần thu thập**:
```
- Email (unique)
- Username (unique)
- Password (strong password policy)
- Full Name (real name)
- Date of Birth (age verification)
- Country/Region
- Phone Number
- Currency preference (USD, VND, THB, etc.)
- ID Document (Passport/National ID)
- Address proof (utility bill, bank statement)
- Selfie with ID (anti-fraud)
```

**KYC Levels**:
- Level 1: Email verified → Can play demo
- Level 2: Phone + ID → Can deposit up to $1,000
- Level 3: Full KYC → Unlimited

### 1.2. User Login
**Chức năng**:
- Email/Username + Password
- 2FA (Google Authenticator, SMS OTP)
- Session management
- Device tracking
- Login history
- IP whitelisting (optional for VIP)
- Biometric login (mobile app)

**Security**:
- Rate limiting (5 failed attempts → captcha)
- Account lockout after 10 failed attempts
- Session timeout (30 mins idle)
- Force logout on password change

### 1.3. Password & Security
- Forgot password (email reset)
- Change password
- Login password (for login)
- Withdrawal password (separate, for security)
- Security questions
- 2FA management

### 1.4. User Profile
**Thông tin cá nhân**:
- Avatar upload
- Full name
- Date of birth
- Email, Phone
- Address
- Currency
- Language preference
- Timezone

**Settings**:
- Email notifications on/off
- SMS notifications
- Push notifications
- Marketing communications opt-in/out
- Session timeout preference
- Display odds format (Decimal, Fractional, American)

### 1.5. Account Verification (KYC)
**KYC Flow**:
1. User uploads documents
2. Admin reviews (manual or AI-assisted)
3. Approve/Reject with reason
4. User can resubmit if rejected

**Documents required**:
- Government ID (Passport, Driver's License, National ID)
- Proof of Address (utility bill < 3 months old)
- Selfie holding ID
- Source of funds (for high rollers)

### 1.6. Responsible Gaming
**Self-Exclusion Tools**:
- Set deposit limits (daily/weekly/monthly)
- Set loss limits
- Set session time limits
- Reality checks (pop-up after X minutes)
- Self-exclusion (cool-off period: 24h, 7d, 30d, permanent)
- Take a break feature

**Links to help**:
- Gambling addiction resources
- Support hotlines
- External organizations (GamCare, BeGambleAware)

---

## MODULE 2: WALLET & PAYMENT SYSTEM

### 2.1. Main Wallet (Cash Credit)
**Chức năng**:
- Display balance in real-time
- Multi-currency support (USD, EUR, VND, THB, CNY, KRW, JPY)
- Currency conversion (real-time rates)
- Balance history
- Separate balances per currency (optional)

**Wallet Types**:
- Main Wallet: For deposits/withdrawals
- Bonus Wallet: For promotional bonuses (với wagering requirements)
- Game Wallet: Balance transferred to game providers

### 2.2. Deposit (Nạp tiền)
**Payment Methods**:
- **Credit/Debit Cards**: Visa, Mastercard, JCB
- **E-wallets**: Skrill, Neteller, PayPal, Perfect Money
- **Bank Transfer**: Local bank transfer, Wire transfer
- **Cryptocurrency**: Bitcoin, Ethereum, USDT, Litecoin
- **Local Methods** (tùy region):
  - Vietnam: Momo, ZaloPay, ViettelPay, Banking
  - Thailand: PromptPay, TrueMoney Wallet
  - China: WeChat Pay, Alipay
  - Korea: Toss, KakaoPay

**Deposit Flow**:
1. Select payment method
2. Enter amount
3. Apply promo code (nếu có)
4. Confirm and redirect to payment gateway
5. Payment processing
6. Auto-credit to wallet (instant for most methods)
7. Email/SMS confirmation

**Deposit Limits**:
- Minimum: $10 (or equivalent)
- Maximum: $50,000 per transaction
- Daily limit: Configurable per user level
- VIP: Higher limits

**Fees**:
- Most deposits: Free
- Credit cards: 2-3% processing fee (optional)
- Crypto: Network fee only

### 2.3. Withdrawal (Rút tiền)
**Withdrawal Methods**:
- Bank transfer (most common)
- E-wallets
- Cryptocurrency (fastest)
- Check (for very large amounts)

**Withdrawal Flow**:
1. Request withdrawal
2. Enter withdrawal password
3. Select method
4. Enter bank details/wallet address
5. Submit
6. Pending review (AML/KYC check)
7. Approved → Payment processing
8. Completed

**Withdrawal Rules**:
- Must verify KYC before first withdrawal
- Wagering requirements must be met (if used bonus)
- Minimum withdrawal: $20
- Maximum per day: $5,000 (standard), $50,000 (VIP)
- Processing time:
  - E-wallets: 0-24 hours
  - Bank transfer: 1-5 business days
  - Crypto: 0-2 hours
  - Checks: 7-14 days

**Withdrawal Fees**:
- First withdrawal per month: Free
- Additional withdrawals: $2-5 fee (or percentage)
- Crypto: Network fee

**Anti-Money Laundering (AML)**:
- Must wager deposit amount at least 1x before withdrawal (rollover requirement)
- Large withdrawals trigger manual review
- Source of funds verification for high amounts

### 2.4. Transaction History
**Display**:
- Date & time
- Type (Deposit, Withdrawal, Bet, Win, Bonus, Refund)
- Amount
- Status (Pending, Completed, Failed, Cancelled)
- Transaction ID
- Payment method
- Balance after transaction

**Filters**:
- Date range
- Type
- Status
- Amount range

**Export**:
- CSV/Excel export
- PDF statement

### 2.5. Bonus Wallet
**Types of Bonuses**:
- Welcome bonus (first deposit)
- Reload bonus (subsequent deposits)
- Cashback (weekly/monthly)
- Free spins
- No deposit bonus
- Referral bonus
- VIP rewards

**Wagering Requirements**:
- Must wager bonus amount X times before withdrawal
- Example: $100 bonus with 30x wagering = must bet $3,000
- Different games contribute differently:
  - Slots: 100%
  - Roulette: 10-20%
  - Blackjack: 10%
  - Baccarat: 5%

**Bonus Terms**:
- Expiry period (usually 30 days)
- Max bet with bonus (e.g., $5 per spin)
- Game restrictions (some games excluded)
- Withdrawal cap (e.g., max win from bonus $500)

### 2.6. Currency Exchange
- Auto-convert on deposit/withdrawal
- Manual exchange in account
- Display exchange rate
- Fee (0.5-2%)

### 2.7. Payment Security
- PCI DSS Level 1 compliance
- SSL/TLS encryption
- Tokenization (never store card details)
- 3D Secure (SCA) for cards
- Fraud detection system
- Withdrawal password (separate from login)

---

## MODULE 3: LIVE CASINO GAMES

### 3.1. Game Halls & Tiers
**Tier Structure** (như trong screenshots):
- **Standard**: Entry-level tables, lower limits
- **Premium**: Mid-range tables, moderate limits
- **Gold**: High-roller tables, higher limits
- **Platinum**: VIP tables, very high limits
- **Diamond**: Ultra VIP, exclusive tables

**Entry Requirements**:
- Standard: Anyone can play
- Gold+: Minimum balance or VIP status
- Diamond: Invitation only, ultra-high net worth

### 3.2. Live Roulette
**Game Logic**:
```
1. Betting Phase (30-60 seconds)
   - Players place chips on betting table
   - Multiple bet types available
   
2. "No More Bets" announced
   - Betting disabled
   
3. Dealer spins wheel
   - Ball released
   - Wait for ball to land
   
4. Winning number announced
   - Ball lands in numbered pocket
   
5. Results calculated
   - All bets settled automatically
   - Winnings credited instantly
   
6. Next round starts
```

**Bet Types & Payouts**:
```
Inside Bets:
- Straight Up (single number): 35:1
- Split (2 numbers): 17:1
- Street (3 numbers): 11:1
- Corner (4 numbers): 8:1
- Six Line (6 numbers): 5:1

Outside Bets:
- Column: 2:1
- Dozen: 2:1
- Red/Black: 1:1 (Even money)
- Odd/Even: 1:1
- High (19-36)/Low (1-18): 1:1
```

**Special Features**:
- Live video stream (HD/4K)
- Multiple camera angles
- Chat với dealer (optional)
- Game history (last 100 spins)
- Hot/Cold numbers statistics
- Betting patterns
- Favorite bets (save common bet combinations)
- Turbo mode (faster rounds)

**Table Limits**:
```
Standard Table:
- Min bet: $0.10 (inside), $1 (outside)
- Max bet: $50 (inside), $1,000 (outside)

Premium Table:
- Min: $1/$10
- Max: $100/$5,000

Gold Table:
- Min: $5/$50
- Max: $500/$25,000

Platinum/Diamond:
- Min: $25/$250
- Max: $5,000/$100,000+
```

### 3.3. Live Baccarat
**Game Logic**:
```
1. Betting Phase
   - Bet on: Player, Banker, or Tie
   - Side bets: Player Pair, Banker Pair, Perfect Pair
   
2. Cards Dealt
   - 2 cards to Player
   - 2 cards to Banker
   
3. Third Card Rules (automatic)
   Player:
   - 0-5: Draw third card
   - 6-7: Stand
   - 8-9: Natural, stand
   
   Banker:
   - Depends on Player's third card and own total
   - (Complex rules, automated by system)
   
4. Compare Totals
   - Closest to 9 wins
   - 10 and face cards = 0
   - Ace = 1
   - Other cards = face value
   - Total > 9: Subtract 10 (e.g., 15 = 5)
   
5. Settle Bets
   - Player win: 1:1
   - Banker win: 1:1 (minus 5% commission)
   - Tie: 8:1 or 9:1
   - Pairs: 11:1
```

**Commission Tracking**:
- 5% commission on Banker wins
- Accumulated and deducted per win or per session
- No-commission Baccarat: Banker win on 6 pays 0.5:1

**Side Bets**:
- Player Pair / Banker Pair: 11:1
- Perfect Pair (same suit): 25:1
- Either Pair: 5:1
- Big (5 or 6 cards dealt): 0.54:1
- Small (4 cards dealt): 1.5:1

**Features**:
- Squeeze feature (slow card reveal for suspense)
- Roadmap displays (Bead Road, Big Road, Big Eye, Small Road, Cockroach)
- Trend analysis
- Score boards

### 3.4. Live Blackjack
**Game Logic**:
```
1. Place Bet
   
2. Cards Dealt
   - 2 cards to each player (face up)
   - 2 cards to dealer (1 face up, 1 face down)
   
3. Player Actions (for each hand)
   - Hit: Take another card
   - Stand: Keep current total
   - Double Down: Double bet, take 1 card, then stand
   - Split: If pair, split into 2 hands (double bet)
   - Surrender: Forfeit half bet (if allowed)
   - Insurance: If dealer shows Ace, bet on dealer blackjack
   
4. Dealer Reveals Hole Card
   - Dealer must hit on 16 or less
   - Dealer must stand on 17 or more (soft 17 rules vary)
   
5. Compare Hands
   - Blackjack (21 with 2 cards): 3:2 or 6:5
   - Closer to 21 wins: 1:1
   - Bust (over 21): Lose
   - Tie (push): Bet returned
```

**Variants**:
- Classic Blackjack
- European Blackjack (dealer gets 2nd card after players)
- Atlantic City Blackjack
- Vegas Strip Blackjack
- Infinite Blackjack (unlimited players per table)

**Side Bets**:
- Perfect Pairs (21+3): Poker-style payouts
- Bet Behind: Bet on other players' hands

### 3.5. Live Poker Games

#### **Ultimate Texas Hold'em** (từ screenshots)
**Game Logic**:
```
1. Place Ante and Blind bets (equal amounts)
   
2. Optional: Place Trips bonus bet
   
3. Receive 2 hole cards
   
4. Decision 1: Play or Check
   - Play (3x or 4x Ante): Make Play bet
   - Check: See flop
   
5. Flop (3 community cards)
   - If checked pre-flop, can Play 2x Ante or Check again
   
6. Turn & River (2 more community cards)
   - If checked flop, must Play 1x Ante or Fold
   
7. Showdown
   - Player and Dealer make best 5-card hand
   - Dealer must qualify with pair or better
   
8. Payouts
   - If dealer doesn't qualify: Ante pushes, Play wins 1:1
   - Player wins: Ante & Play win 1:1, Blind pays per paytable
   - Player loses: Lose all bets
   - Tie: Push
   
   Blind Paytable:
   - Royal Flush: 500:1
   - Straight Flush: 50:1
   - Four of a Kind: 10:1
   - Full House: 3:1
   - Flush: 3:2
   - Straight: 1:1
   - Less than straight: Push
```

**Trips Bonus** (optional side bet):
```
- Royal Flush: 50:1
- Straight Flush: 40:1
- Four of a Kind: 30:1
- Full House: 8:1
- Flush: 7:1
- Straight: 4:1
- Three of a Kind: 3:1
```

#### **Caribbean Stud Poker**
#### **Three Card Poker**
#### **Casino Hold'em**

### 3.6. Live Pai Gow (從 screenshots)
**Game Rules**:
```
Pai Gow Poker combines poker with Chinese Pai Gow domino game.

1. Each player receives 7 cards
   
2. Split into:
   - 5-card hand (High hand)
   - 2-card hand (Low hand)
   - Rule: 5-card hand MUST be higher than 2-card hand
   
3. Dealer reveals their 7 cards and sets hands (house way)
   
4. Compare:
   - Player wins both hands: Win 1:1 (minus 5% commission)
   - Dealer wins both hands: Lose bet
   - Each wins one hand (push/copy): Tie, bet returned
   
5. Bonus for specific hands (optional):
   - 7-card Straight Flush
   - Royal Flush + Royal Match
   - Five Aces
```

**House Way**: Dealer follows preset rules for setting hands (optimal strategy)

### 3.7. Other Live Games
- **Dragon Tiger**: Simple high-card game
- **Sic Bo**: Chinese dice game
- **Craps**: Dice game
- **Money Wheel / Dream Catcher**: Big spinning wheel
- **Monopoly Live**: Bonus game
- **Lightning Roulette**: Multipliers
- **Crazy Time**: Game show style

### 3.8. Game Room Features
**Common Features Across All Games**:
- HD live video streaming (multiple quality options)
- Multiple camera angles
- Picture-in-picture mode
- Chat with dealer (emoji, preset messages)
- Chat with other players (can be disabled)
- Betting history
- Game statistics
- Sound on/off
- Full screen mode
- Multi-table play (play multiple games simultaneously)

**Game Info Display**:
- Dealer name
- Table ID
- Min/Max bets
- Number of players at table
- Countdown timer for betting
- Last results (e.g., last 20 rounds)

**Betting Interface**:
- Chip selector (different denominations)
- Click betting area to place bet
- Drag chips to cancel
- "Undo" button
- "Double" button (double all bets)
- "Rebet" button (repeat last bet)
- "Clear All" button
- Total bet display
- Potential win display

### 3.9. Game History & Statistics
**Per Game**:
- Last 100 results
- Hot/Cold numbers (Roulette)
- Bead Road, Big Road (Baccarat)
- Win rate statistics
- Biggest wins today

**Personal History**:
- All bets placed
- Win/loss per session
- Favorite games
- Time spent playing

---

## MODULE 4: SLOT GAMES (Lucky 28, etc.)

### 4.1. Slot Game Types
**From Screenshots**:
- Lucky 28 (themed với football stars: Mbappé, Ronaldo)
- British Lucky 28
- Korean Lucky 28
- Taiwan Times
- Various themed slots

**Standard Slot Types**:
- **Classic Slots**: 3 reels, simple gameplay
- **Video Slots**: 5+ reels, bonus features
- **Progressive Jackpot Slots**: Pooled jackpot across network
- **Megaways**: Variable paylines (up to 100,000+)
- **Branded Slots**: Movie/TV/celebrity themes

### 4.2. Slot Game Logic

**Core Mechanics**:
```
1. Select Bet Amount
   - Coin value: $0.01 - $10
   - Coins per line: 1-10
   - Number of paylines: 1-50+
   - Total bet = coin value × coins × lines
   
2. Spin Reels
   - RNG (Random Number Generator) determines outcome
   - Reels spin and stop
   
3. Check for Winning Combinations
   - Match symbols on active paylines
   - Left to right (most games)
   - Any position (scatter symbols)
   
4. Calculate Payout
   - Based on paytable
   - Multiply bet per line × symbol multiplier
   
5. Trigger Bonus Features (if applicable)
   - Free Spins
   - Bonus Games
   - Multipliers
   - Wild expansions
   
6. Add Winnings to Balance
```

**RNG (Random Number Generator)**:
- Provably fair algorithm
- Certified by gaming labs (eCOGRA, GLI, iTech Labs)
- Cannot be manipulated
- Results determined at moment of spin

**RTP (Return to Player)**:
- Theoretical payout percentage
- Example: 96% RTP = long-term, for every $100 wagered, $96 returned
- Displayed in game info
- Typical range: 92-98%

**Volatility**:
- Low: Frequent small wins
- Medium: Balance of frequency and size
- High: Rare but big wins

### 4.3. Slot Features

**Wild Symbols**:
- Substitute for other symbols (except scatter/bonus)
- Expanding Wilds: Cover entire reel
- Sticky Wilds: Remain for multiple spins
- Walking Wilds: Move across reels
- Stacked Wilds: Multiple wilds on same reel

**Scatter Symbols**:
- Pay anywhere on reels (not just paylines)
- Trigger free spins or bonuses
- Usually need 3+ to trigger

**Free Spins**:
- Triggered by scatter symbols
- Play X spins without betting
- Often with multipliers or special features
- Can re-trigger during free spins

**Bonus Games**:
- Pick-and-win: Choose items for prizes
- Wheel of Fortune: Spin for prize
- Interactive mini-games
- Triggered by bonus symbols

**Multipliers**:
- 2x, 3x, 5x, etc.
- Multiply winnings
- Can be in base game or free spins

**Cascading Reels / Avalanche**:
- Winning symbols disappear
- New symbols fall to replace
- Chain reactions possible
- Used in games like Gonzo's Quest

**Gamble Feature**:
- Double-or-nothing
- Guess card color (red/black) or suit
- Can lose everything

### 4.4. Progressive Jackpots
**How It Works**:
```
1. Small % of each bet goes to jackpot pool
2. Jackpot grows until someone wins
3. Can be:
   - Local: Single casino
   - Network: Multiple casinos
   - Must-drop: Must pay out by certain amount/time
   
4. Trigger:
   - Random (any spin can trigger)
   - Bonus game
   - Special symbol combination
   
5. Levels:
   - Mini: $10-100
   - Minor: $100-1,000
   - Major: $1,000-10,000
   - Grand/Mega: $10,000-millions
```

**Famous Progressive Slots**:
- Mega Moolah (record: $20+ million)
- Mega Fortune
- Hall of Gods
- Arabian Nights

### 4.5. Slot Game Interface
**Controls**:
- Spin button (large, center)
- Auto-spin: Set number of spins
- Max Bet button
- Bet adjustment (+/-)
- Paylines selection
- Paytable button (view rules and payouts)
- Settings (sound, animations, speed)
- Game info (RTP, rules, features)
- Balance display
- Win display
- History button

**Auto-Play Features**:
- Number of spins (10, 25, 50, 100, ∞)
- Stop conditions:
  - On any win
  - If single win exceeds $X
  - If balance increases by $X
  - If balance decreases by $X
  - On bonus feature

**Turbo/Quick Spin**:
- Speed up animations
- Instant reel stop

### 4.6. Lucky 28 Specific (Based on Screenshots)
**Appears to be**:
- Themed slot with football/sports celebrities
- Different variants (British, Korean, Taiwan)
- Minimum bet visible: 200 USD (high roller game)
- Time limit: 5 minutes per session
- Tier-based access (Gold, Platinum, Diamond)

**Possible Mechanics**:
- Celebrity symbols (Mbappé, Ronaldo, etc.)
- Lucky number 28 as special symbol
- Regional variants for different markets
- High-stakes gameplay
- Exclusive for VIP players

---

## MODULE 5: LOTTERY & LUCKY NUMBER GAMES

### 5.1. Tích Lũy Phần Thưởng (Accumulation Rewards)
From screenshot showing "TÍCH LŨY PHẦN THƯỞNG" with prize tiers:

**Prize Tiers Shown**:
```
6,000$    → 388$
10,000$   → 888$
20,000$   → 1,888$
50,000$   → 4,888$
100,000$  → 8,888$
200,000$  → 18,888$
300,000$  → 28,888$
400,000$  → 38,888$
500,000$  → 48,888$
700,000$  → 68,888$
1,000,000$ → 88,888$
2,000,000$ → 188,888$
5,000,000$ → 488,888$
8,888,888$ → 888,888$
```

**Mechanics**:
- Accumulate betting volume
- When total bets reach threshold, receive bonus
- Higher tiers give bigger rewards
- Rewards automatically credited
- Progress bar showing current accumulation
- Valid period (e.g., monthly)

### 5.2. Quay Số (Spin/Draw Games)
**Features from screenshots**:
- Live draw results
- Historical results (Lịch sử quay số)
- Bet history (Lịch sử cược)
- Real-time participation
- Multiple betting options

**Game Types**:
- **Keno**: Pick numbers, draw happens
- **Lottery**: Buy tickets with numbers
- **Number Games**: Predict outcomes
- **Instant Win**: Scratch cards

### 5.3. Number Prediction Games
**Common Formats**:
- Pick X numbers from Y (e.g., pick 6 from 49)
- Match numbers for prizes
- Multiple prize tiers
- Daily/weekly/monthly draws

---

## MODULE 6: VIP & LOYALTY PROGRAM

### 6.1. VIP Tier Levels
**Membership Tiers** (from screenshots):
- **Member**: Default (red badge shown)
- **Bronze**: Entry VIP
- **Silver**: Mid VIP
- **Gold**: High VIP
- **Platinum**: Very High VIP
- **Diamond**: Ultimate VIP

**Progression**:
- Based on total wagering volume
- Or deposit amount
- Or combination of both
- Cannot be purchased (earn through play)

**Benefits by Tier**:
```
Member:
- Standard access
- Basic support

Bronze:
- 5% cashback
- Birthday bonus $50
- Dedicated support

Silver:
- 8% cashback
- Priority withdrawals (24h)
- Birthday bonus $100
- Personal account manager

Gold:
- 10% cashback
- Faster withdrawals (12h)
- Access to Gold game halls
- Birthday bonus $500
- Higher deposit/withdrawal limits
- Invitations to events

Platinum:
- 12% cashback
- Instant withdrawals
- Access to Platinum halls
- Birthday bonus $2,000
- VIP gifts
- Exclusive tournaments
- Personal host

Diamond:
- 15% cashback
- Immediate withdrawals
- Access to Diamond exclusive tables
- Birthday bonus $10,000
- Luxury gifts
- Private jet arrangements (top tier)
- Invites to major events (Monaco, Las Vegas)
- Dedicated 24/7 host
```

### 6.2. Rewards & Cashback
**Weekly Cashback**:
- % of net losses returned
- Calculated Monday (previous week)
- No wagering requirements
- Auto-credited

**Monthly Bonus**:
- Based on total wagering
- Bonus with wagering requirements
- Loyalty points

**Loyalty Points**:
- Earn points for every bet
- Exchange for cash/bonuses
- Different games earn different rates:
  - Slots: 10 points per $10
  - Table games: 5 points per $10

### 6.3. Referral Program (Refer a Friend)
From screenshot showing "Lời mời" (Invitation) page:

**Features**:
- Unique referral code (e.g., "B2USQH")
- Referral link (e.g., "resortworld...f/B2USQH")
- Copy button for easy sharing
- QR code for mobile

**Rewards**:
- Referrer earns: % of friend's deposits or wagering
- Referee earns: Welcome bonus
- Lifetime commission (continuous earnings)
- Tiered commission:
  - Friend deposits $100: Earn $10
  - Friend deposits $1,000: Earn $150
  - Etc.

---

## MODULE 7: GAME PROVIDERS INTEGRATION

### 7.1. Live Casino Providers
**Major Providers**:
- **Evolution Gaming** (market leader)
  - Extensive live dealer games
  - Innovative game shows
  - High quality studios
  
- **Pragmatic Play Live**
  - Popular in Asia
  - Megaways slots
  
- **Playtech**
  - Diverse portfolio
  - Marvel slots
  
- **Ezugi**
  - Good for Asian markets
  
- **Asia Gaming** (AG) - From screenshot
  - Popular in Asian markets
  - Local language dealers
  
- **Microgaming** - From screenshot
  - Huge game library
  - Progressive jackpots

- **BGaming** - From screenshot
- **Other Providers Shown**: Evolution Gaming logo visible

### 7.2. Slot Providers
- NetEnt
- Microgaming
- Pragmatic Play
- Play'n GO
- Yggdrasil
- Red Tiger
- Quickspin
- BTG (Big Time Gaming)
- Push Gaming

### 7.3. Integration Methods
**Seamless Integration**:
```
User → Platform → Game Provider API → Game Launches

1. User clicks game
2. Platform requests game session from provider
3. Provider returns game URL with token
4. Game loads in iframe/new window
5. Bets deducted from platform wallet
6. Wins credited back to platform
7. Session ends, final balance synced
```

**API Requirements**:
- Authentication (API key/secret)
- Player ID mapping
- Balance query
- Debit/credit calls
- Transaction callbacks
- Game history retrieval
- Bonus integration
- Free rounds management

**Wallet Types**:
- **Transfer Wallet**: Money moved to provider wallet
- **Seamless Wallet**: Real-time debit/credit (better UX)

---

## MODULE 8: ADMIN & BACKOFFICE

### 8.1. Dashboard
**Key Metrics**:
- Total revenue (today/week/month/year)
- Active players (online now)
- New registrations
- Total deposits
- Total withdrawals
- Net gaming revenue (NGR)
- Gross gaming revenue (GGR)
- Pending withdrawals count
- KYC pending count

**Charts**:
- Revenue trend
- Player activity
- Game popularity
- Win/loss by game
- Deposit methods breakdown
- Country distribution

### 8.2. Player Management
**Player List**:
- Search by: username, email, player ID
- Filters: VIP level, status, country, registration date
- Columns: ID, Username, Email, Balance, VIP, Status, Last Login

**Player Details**:
- Personal info
- KYC documents (view/approve/reject)
- Balance (all wallets)
- Transaction history
- Game history
- Bet history
- Bonus history
- Login history
- Communication logs
- Notes (internal)

**Actions**:
- View profile
- Edit details
- Verify/Unverify KYC
- Adjust balance (manual correction)
- Award bonus
- Set VIP level
- Set limits (deposit/loss/session)
- Suspend account
- Ban account
- Send message
- Add note

### 8.3. Transaction Management
**Deposit Management**:
- Pending deposits
- Approve/Reject manual deposits
- View payment gateway status
- Refund deposits

**Withdrawal Management**:
- Pending withdrawals queue
- Review each withdrawal
- Check for:
  - KYC verified
  - Wagering met
  - AML flags
  - Duplicate withdrawals
  - Unusual patterns
- Approve/Reject/Hold
- Batch processing
- Manual payout (mark as paid)

**Transaction History**:
- All transactions across platform
- Export to Excel/CSV
- Reconciliation reports

### 8.4. Game Management
**Game Library**:
- Add/remove games
- Enable/disable games
- Set game order (featured)
- Upload game images/thumbnails
- Set RTP display
- Set min/max bets per game
- Game categories
- Tags (new, hot, popular)

**Live Casino Tables**:
- Create/edit tables
- Set table limits
- Assign dealers
- Table schedule
- Maintenance mode

**Provider Management**:
- Enable/disable providers
- API credentials
- Provider balance
- Sync games from provider

### 8.5. Bonus & Promotion Management
**Create Bonus Campaign**:
- Bonus type (deposit, cashback, free spins)
- Bonus amount/percentage
- Wagering requirements
- Eligible games
- Max bet with bonus
- Max win cap
- Valid period
- Target audience (all, VIP only, country)
- Promo code (optional)
- Terms & conditions

**Active Promotions List**:
- Edit/pause/stop campaigns
- View statistics (claims, wagering, conversions)
- Clone campaign

**Manual Bonuses**:
- Award to specific player
- Goodwill bonuses
- Compensation

### 8.6. VIP Management
**VIP Players List**:
- Current tier
- Points to next level
- Lifetime value (LTV)
- Recent activity

**Manual Tier Adjustment**:
- Promote/demote player
- Award VIP benefits
- Send VIP offers

**VIP Communication**:
- Send personalized offers
- Event invitations
- Gift tracking

### 8.7. Reports & Analytics

**Financial Reports**:
- Revenue report (GGR, NGR)
- Deposits vs. withdrawals
- Payment method report
- Provider fees
- Bonus costs
- Net profit

**Player Reports**:
- New registrations
- Active players (DAU, WAU, MAU)
- Player retention
- Churn rate
- LTV (Lifetime Value)
- Segmentation (whales, regulars, casuals)

**Game Reports**:
- Game performance (by game)
- Game type performance (slots vs. live casino)
- RTP actual vs. theoretical
- Popular games
- Provider performance

**Risk & Fraud Reports**:
- Suspicious activities
- Multiple accounts
- Bonus abuse
- Failed KYC
- Chargebacks

**Export Options**:
- PDF
- Excel
- CSV
- Scheduled reports (email daily/weekly)

### 8.8. CMS (Content Management)
**Homepage**:
- Hero banners (carousel)
- Featured games
- Promotions section
- Winners showcase

**Pages**:
- About us
- Terms & conditions
- Privacy policy
- Responsible gaming
- FAQ
- Game rules
- Payment methods page
- VIP page

**Blog/News**:
- Articles
- Game guides
- Winner stories
- Company news

### 8.9. Settings

**General**:
- Site name
- Logo upload
- Favicon
- Currencies
- Languages
- Timezone
- Maintenance mode

**Payment Settings**:
- Enable/disable payment methods
- API credentials
- Min/max limits
- Fees
- Auto-approval thresholds

**Email Settings**:
- SMTP config
- Email templates (welcome, deposit, withdrawal, etc.)
- Sender name/email

**SMS Settings**:
- Provider (Twilio, etc.)
- Templates
- Cost tracking

**Game Settings**:
- Default RTP display
- Demo mode on/off
- Currency conversion rates

**Security**:
- 2FA enforcement
- Session timeout
- IP whitelist for admin
- Login attempt limits
- Withdrawal password requirement

**Limits**:
- Global max bet
- Default deposit limits
- Default withdrawal limits
- Wagering requirement for deposits

**Compliance**:
- License details
- RNG certificates
- Responsible gaming tools
- Self-exclusion period options
- Age verification strictness

### 8.10. Support & Communication

**Live Chat (Admin Side)**:
- Queue of chat requests
- Assign to agent
- Canned responses
- Chat history
- Transfer to supervisor

**Ticket System**:
- Open tickets
- Assign/resolve
- Priority levels
- Categories

**Email System**:
- Send mass emails
- Segmented lists
- Open/click rates

**Push Notifications**:
- Send to all or segments
- Schedule
- Track delivery

### 8.11. Compliance & Logs

**Activity Logs**:
- Admin actions log
- Player actions log
- System events

**Audit Trail**:
- Who did what and when
- Cannot be altered

**Responsible Gaming Monitoring**:
- Players who set limits
- Self-excluded players
- High-risk behavior alerts
- Problem gambling indicators

**AML/KYC Tracking**:
- KYC verification status
- Source of wealth documentation
- Large transaction monitoring
- PEP (Politically Exposed Person) checks

---

## MODULE 9: MOBILE APP

### 9.1. Mobile-Specific Features
- Biometric login (Face ID, Touch ID, fingerprint)
- Push notifications
- QR code scanner (for deposits, referrals)
- Quick balance check widget
- Offline mode (view history, no gameplay)
- Location services (for geo-restricted markets)

### 9.2. Mobile Navigation
**Bottom Tab Bar**:
- Home (Trang chủ)
- Games (Trò chơi)
- History (Lịch sử quay số / cược)
- Profile (Hồ sơ)
- More/Menu

### 9.3. Mobile Optimization
- Touch-friendly buttons (min 44x44px)
- Swipe gestures
- Landscape mode for games
- Reduced data usage mode
- Quick deposit from home screen
- One-tap bet repeat

---

## MODULE 10: COMPLIANCE, SECURITY & LEGAL

### 10.1. Gaming License
**Requirements**:
- Must obtain license from jurisdiction:
  - Curacao eGaming
  - Malta Gaming Authority (MGA)
  - UK Gambling Commission (UKGC)
  - Philippine PAGCOR
  - Gibraltar
  - Kahnawake
  
- Display license number on site
- Regular audits
- RNG certification
- Fair gaming certificate

### 10.2. Responsible Gaming
**Mandatory Tools**:
- Deposit limits
- Loss limits
- Session limits
- Reality checks
- Self-exclusion (24h, 7d, 30d, 6m, permanent)
- Self-assessment questionnaire
- Links to help organizations

**Problem Gambling Detection**:
- Spending spike alerts
- Chasing losses pattern
- Long session alerts
- Frequent deposit attempts
- Admin intervention capability

### 10.3. KYC/AML Compliance
**KYC (Know Your Customer)**:
- Identity verification
- Address verification
- Age verification (18+/21+)
- Source of funds (for high rollers)
- Enhanced due diligence (EDD) for VIPs

**AML (Anti-Money Laundering)**:
- Transaction monitoring
- Suspicious activity reports (SAR)
- Large transaction alerts ($10,000+)
- PEP screening
- Sanctions list checking
- Minimum wagering before withdrawal (1x deposit)

### 10.4. Data Protection (GDPR)
**Compliance**:
- Privacy policy
- Cookie consent
- Data processing agreement
- Right to access data
- Right to deletion
- Right to portability
- Data breach notification (72h)
- DPO (Data Protection Officer)

### 10.5. Security Measures
**Technical Security**:
- SSL/TLS 256-bit encryption
- DDoS protection (Cloudflare)
- WAF (Web Application Firewall)
- Regular security audits
- Penetration testing
- Secure API keys storage
- Database encryption
- Secure password hashing (bcrypt)
- CSRF protection
- XSS prevention
- SQL injection prevention

**Operational Security**:
- Two-factor authentication (2FA)
- Withdrawal password
- Email verification for sensitive actions
- Login alerts
- Device fingerprinting
- IP tracking
- Session management
- Auto-logout on inactivity

**Financial Security**:
- Segregated player funds
- Independent auditors
- Bankroll management
- Insurance policies
- Regular financial reporting

### 10.6. Fair Gaming
**RNG Certification**:
- Certified by:
  - eCOGRA
  - GLI (Gaming Laboratories International)
  - iTech Labs
  - BMM Testlabs
  
- Regular RNG testing
- Provably fair games (crypto casinos)
- RTP verification
- Game history verification

**Transparency**:
- Display RTP for all games
- Publish payout percentages
- Game rules easily accessible
- Paytables visible
- Terms & conditions clear

### 10.7. Age Verification
- Date of birth check
- ID document verification
- Credit card age check
- Third-party age verification services
- Block underage access immediately

### 10.8. Geo-Blocking
**Restricted Countries**:
- USA (most states)
- France
- Netherlands
- Australia
- UK (need UKGC license)
- Others depending on local laws

**Implementation**:
- IP-based blocking
- GPS location check (mobile)
- Payment method country check
- Manual verification if needed

### 10.9. Terms & Conditions
**Must Include**:
- Eligibility (age, location)
- Account rules
- Deposit/withdrawal terms
- Bonus terms
- Betting rules
- Cancellation policy
- Dispute resolution
- Liability limitations
- Prohibited activities
- Account closure
- Inactive account policy

### 10.10. Payment Security (PCI DSS)
- Never store CVV
- Tokenize card details
- PCI DSS Level 1 compliance
- 3D Secure (SCA)
- Fraud detection systems
- Chargeback handling

---

## MODULE 11: ADDITIONAL FEATURES

### 11.1. Tournaments
**Tournament Types**:
- Slot tournaments
- Poker tournaments
- Leaderboard competitions
- Prize pools
- Real-time rankings

### 11.2. Gamification
- Achievements/badges
- Missions/quests
- Daily login rewards
- Spin the wheel (daily bonus)
- Level system
- Progress bars

### 11.3. Social Features
- Friend list
- Send gifts
- Challenge friends
- Share wins (social media)
- Chat rooms
- Emojis/stickers

### 11.4. Live Support
- 24/7 live chat
- Multi-language support
- Chatbot for FAQs
- Email support
- Phone support (VIP)
- Social media support

### 11.5. Localization
**Languages** (from screenshots showing):
- Tiếng Việt ✓
- English
- 中文 (Chinese)
- Malay
- 日本語 (Japanese)
- 한국어 (Korean)

**Currency Support**:
- USD (primary)
- VND (Vietnam Dong)
- THB (Thai Baht)
- CNY (Chinese Yuan)
- KRW (Korean Won)
- MYR (Malaysian Ringgit)
- JPY (Japanese Yen)
- Cryptocurrency (BTC, ETH, USDT)

**Regional Customization**:
- Payment methods per region
- Game preferences per region
- Marketing per region
- Time zones
- Number formats
- Date formats

---

## PRIORITIZATION & MVP ROADMAP

### Phase 1: MVP (4-6 months)
**Essential Features**:
1. ✅ User registration & login
2. ✅ KYC basic (upload docs)
3. ✅ Wallet system (deposit/withdrawal)
4. ✅ Payment integration (2-3 methods)
5. ✅ Game provider integration (1-2 providers)
   - Evolution Gaming (live casino)
   - Pragmatic Play (slots)
6. ✅ Basic game catalog (20-30 games)
   - Roulette
   - Blackjack
   - Baccarat
   - 20 slot games
7. ✅ Basic admin panel
   - Player management
   - Transaction management
   - Game management
8. ✅ Responsible gaming tools (basic limits)
9. ✅ Mobile responsive web
10. ✅ Live chat support

**MVP Goal**: Functional casino with core games, can accept players

### Phase 2: Core Features (2-3 months)
1. More game providers (5+ total)
2. More games (100+ games)
3. Bonus system
4. VIP program
5. Referral program
6. Advanced admin (reports, analytics)
7. Email/SMS notifications
8. Payment methods expansion (10+)
9. More languages (5 languages)
10. Advanced KYC automation

### Phase 3: Advanced Features (2-3 months)
1. Native mobile apps (iOS/Android)
2. Tournaments
3. Gamification
4. Sports betting module
5. Lottery games
6. Live streaming enhancement
7. AI-powered recommendations
8. Advanced fraud detection
9. Multi-currency wallet
10. Cryptocurrency integration

### Phase 4: Premium Features (3-4 months)
1. VR casino (future tech)
2. Blockchain integration
3. NFT rewards
4. Metaverse presence
5. Advanced AI chatbot
6. Personalization engine
7. Social casino features
8. White label solution
9. Affiliate system
10. Advanced analytics (BI)

---

## TECHNICAL REQUIREMENTS SUMMARY

### Must-Have Tech Stack:
- **Backend**: Node.js/Python/PHP (with strong security)
- **Database**: PostgreSQL + Redis
- **Game Integration**: RESTful API or WebSocket
- **Payment**: Multiple gateways + crypto
- **Security**: SSL, 2FA, encryption, DDoS protection
- **Compliance**: License, RNG certification, audits
- **Hosting**: High-availability cloud (AWS/GCP)
- **CDN**: For fast game loading globally
- **Monitoring**: Real-time system monitoring
- **Backup**: Automated daily backups

### Performance Targets:
- Page load: < 2 seconds
- Game launch: < 3 seconds
- API response: < 200ms
- Uptime: 99.9%
- Support response: < 2 minutes

### Scalability:
- Support 10,000+ concurrent players
- Handle 1M+ bets per day
- Process $10M+ transactions per month

---

## ESTIMATED COSTS

### Development:
- MVP: $80,000 - $150,000
- Full Platform: $200,000 - $500,000

### Licensing:
- Curacao: $15,000 - $25,000/year
- Malta: $50,000+/year
- RNG certification: $5,000 - $15,000

### Operations (Monthly):
- Hosting: $2,000 - $10,000
- Game provider fees: 10-20% revenue share
- Payment processing: 2-5% + fees
- Support staff: $5,000 - $20,000
- Marketing: $10,000 - $100,000+

### Total Initial Investment: $200,000 - $700,000

---

## LEGAL & COMPLIANCE CHECKLIST

- [ ] Obtain gaming license
- [ ] Register company in jurisdiction
- [ ] Get RNG certification
- [ ] Implement KYC/AML procedures
- [ ] Create comprehensive T&C
- [ ] Privacy policy (GDPR compliant)
- [ ] Responsible gaming tools
- [ ] Payment processor agreements
- [ ] Game provider contracts
- [ ] Insurance policies
- [ ] Regular audits schedule
- [ ] Dispute resolution process
- [ ] Data protection officer
- [ ] Security audit
- [ ] Penetration testing

---

**Document Version**: 1.0  
**Date**: 2026-08-19  
**Status**: Draft for Review

*Kế hoạch này là phân tích chi tiết dựa trên screenshots và research về casino online. Cần điều chỉnh theo requirements cụ thể và regulations của thị trường mục tiêu.*
