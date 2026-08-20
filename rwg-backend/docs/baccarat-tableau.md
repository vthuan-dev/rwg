# Baccarat Tableau - Luật rút lá bài thứ ba (Third Card Rules)

Tài liệu này định nghĩa chính xác và chi tiết luật rút lá bài thứ ba cho trò chơi Baccarat tại Resort World Genting (RWG). Đây là bảng Tableau chính thức được khóa theo quy ước **M3** trong [DECISIONS.md](file:///d:/Project/RWG/DECISIONS.md).

---

## 1. Giá trị các lá bài (Card Values)

*   Các lá từ **2 đến 9**: Giá trị bằng đúng con số trên lá bài (face value).
*   Các lá **10, J, Q, K**: Giá trị là **0**.
*   Lá **A (Ace)**: Giá trị là **1**.
*   **Tổng điểm của một cửa**: Bằng tổng giá trị các lá bài cộng lại, lấy chữ số hàng đơn vị (tổng điểm modulo 10). Điểm số tối đa là **9**.

---

## 2. Quy tắc cho 2 lá bài đầu tiên (Initial Two Cards)

Khi bắt đầu ván bài, cả hai cửa **Player** và **Banker** đều được chia đúng 2 lá bài.

### 2.1. Thắng tự nhiên (Natural Win)
Nếu một trong hai cửa (hoặc cả hai) có tổng điểm 2 lá đầu tiên là **8** hoặc **9**:
*   Ván bài kết thúc ngay lập tức.
*   **Không** bên nào được rút thêm lá bài thứ ba.
*   So sánh điểm để phân định thắng, thua hoặc hòa.

Nếu không có cửa nào đạt 8 hoặc 9, hệ thống tiến hành xét luật rút lá thứ ba dưới đây.

---

## 3. Luật rút lá bài thứ ba của Player (Player's Rule)

Luật của Player cực kỳ đơn giản và được xét trước tiên:

| Tổng điểm 2 lá đầu của Player | Hành động của Player |
| :---: | :--- |
| **0, 1, 2, 3, 4, 5** | **RÚT** thêm lá thứ ba (Draw) |
| **6, 7** | **DỪNG** (Stand) - Không rút thêm |
| **8, 9** | **Thắng tự nhiên** - Ván bài kết thúc |

---

## 4. Luật rút lá bài thứ ba của Banker (Banker's Rule)

Luật của Banker phức tạp hơn, phụ thuộc vào:
1.  Tổng điểm 2 lá đầu của Banker.
2.  Player có rút lá thứ ba hay không.
3.  **Giá trị của lá bài thứ ba mà Player vừa rút** (chứ không phải tổng điểm của Player).

### 4.1. Trường hợp Player KHÔNG rút lá thứ ba (Player Stands on 6 or 7)
Nếu Player dừng ở 6 hoặc 7, luật của Banker áp dụng tương tự Player:

| Tổng điểm 2 lá đầu của Banker | Hành động của Banker |
| :---: | :--- |
| **0, 1, 2, 3, 4, 5** | **RÚT** thêm lá thứ ba (Draw) |
| **6, 7** | **DỪNG** (Stand) |

### 4.2. Trường hợp Player CÓ rút lá thứ ba
Nếu Player đã rút lá thứ ba, Banker sẽ quyết định Rút (D) hoặc Dừng (S) dựa theo bảng Tableau dưới đây:

| Điểm Banker | Lá bài thứ ba của Player là gì? | | | | | | | | | | Hành động của Banker |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| | **0 / 1** | **2** | **3** | **4** | **5** | **6** | **7** | **8** | **9** | **A (1)** | |
| **0, 1, 2** | D | D | D | D | D | D | D | D | D | D | **Luôn RÚT** |
| **3** | D | D | D | D | D | D | D | **S** | D | D | **Rút** trừ khi lá thứ 3 của Player là **8** |
| **4** | **S** | D | D | D | D | D | D | **S** | **S** | **S** | **Rút** nếu lá thứ 3 của Player là **2 - 7** |
| **5** | **S** | **S** | **S** | D | D | D | D | **S** | **S** | **S** | **Rút** nếu lá thứ 3 của Player là **4 - 7** |
| **6** | **S** | **S** | **S** | **S** | **S** | D | D | **S** | **S** | **S** | **Rút** nếu lá thứ 3 của Player là **6 - 7** |
| **7** | **S** | **S** | **S** | **S** | **S** | **S** | **S** | **S** | **S** | **S** | **Luôn DỪNG** |

*   **D**: Draw (Rút)
*   **S**: Stand (Dừng)

---

## 5. Xác định kết quả và Payout (Thanh toán)

Sau khi rút bài xong (nếu có), so sánh tổng điểm cuối cùng của Player và Banker để xác định thắng/thua:

### 5.1. Các cửa cược chính & Tỷ lệ trả thưởng (Odds)
Quy ước stake-inclusive **M2** (DECISIONS.md): Thắng nhận lại tiền cược gốc + tiền lời theo Odds.

1.  **Cược cửa PLAYER thắng (PLAYER)**:
    *   Odds: **1:1**.
    *   Ví dụ: Cược $10 thắng → nhận lại tổng cộng `$10 (gốc) + $10 = $20`.
2.  **Cược cửa BANKER thắng (BANKER)**:
    *   Odds: **1:1** trừ **5% commission** tính trên tiền lời (theo quy ước **M6**).
    *   Tiền nhận lại = `Stake + Stake × 1 - 5% × Stake = Stake × 1.95`.
    *   Ví dụ: Cược $10 thắng → nhận lại tổng cộng `$10 (gốc) + $9.50 = $19.50`.
3.  **Cược cửa HÒA (TIE)**:
    *   Odds: **8:1** (theo quy ước **M4**).
    *   Ví dụ: Cược $10 thắng → nhận lại tổng cộng `$10 (gốc) + $80 = $90`.
    *   **LƯU Ý KHI KẾT QUẢ LÀ TIE (Hòa)**:
        *   Nếu kết quả ván bài là Hòa, những người cược cửa `PLAYER` hoặc `BANKER` sẽ được **hoàn lại nguyên vẹn tiền cược** (không thắng, không thua).

### 5.2. Các cửa cược phụ (Side Bets)
4.  **Cược PLAYER PAIR**:
    *   Odds: **11:1** (theo quy ước **M7**).
    *   Thắng nếu **2 lá đầu tiên** của Player tạo thành một đôi (cùng hạng, ví dụ: 2 lá K, hoặc 2 lá 8; không cần cùng chất).
5.  **Cược BANKER PAIR**:
    *   Odds: **11:1** (theo quy ước **M7**).
    *   Thắng nếu **2 lá đầu tiên** của Banker tạo thành một đôi.
