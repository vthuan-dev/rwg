"use client";

import React, { useState, useEffect, useRef, useCallback } from "react";
import Link from "next/link";
import {
  Volume2,
  VolumeX,
  HelpCircle,
  Trophy,
  Sparkles,
  ChevronLeft,
  ChevronRight,
  MessageSquare,
  AlertTriangle,
  RefreshCw,
  Crown,
  History,
  TrendingUp,
  Send,
  ShieldAlert,
  Bot,
  X,
} from "lucide-react";
import { me, walletMe, betsHistory, getExchangeRate, getXocDiaConfig, getXocDiaJackpot, PlayerBet, placeBet as apiPlaceBet, gameTables, currentRound, myBets, roundsHistory, parseXocDiaCoins, tableOdds, TableOdds, GameRound, ApiError } from "@/lib/playerApi";
import { mergeServerOdds } from "@/lib/betOptions";
import XocDiaCanvas from "./XocDiaCanvas";
import { RubyDice } from "./RubyDice";
import { JackpotCoinShower } from "./JackpotCoinShower";

type Phase = "BETTING_OPEN" | "BETTING_CLOSED" | "SPINNING" | "RESULT" | "SETTLE";

interface BetState {
  XOC_DIA_EVEN: number;
  XOC_DIA_ODD: number;
  XOC_DIA_FOUR_RED: number;
  XOC_DIA_FOUR_WHITE: number;
  XOC_DIA_THREE_RED: number;
  XOC_DIA_THREE_WHITE: number;
}

interface RoundHistory {
  seq: number;
  redCount: number;
  isEven: boolean;
}

interface FlyingChip {
  id: number;
  zone: keyof BetState;
  fromX: number;
  fromY: number;
  toX: number;
  toY: number;
  val: number;
  img: string;
}

interface TableChip {
  id: number;
  playerId: string;
  zone: keyof BetState;
  val: number;
  img: string;
  x: number;
  y: number;
  rotation: number;
}

interface ReturnChip {
  id: number;
  playerId: string;
  fromX: number;
  fromY: number;
  toX: number;
  toY: number;
  img: string;
  delayMs: number;
  size?: number;
  hero?: boolean;
  durationMs?: number;
}

interface PayoutBurst {
  id: number;
  x: number;
  y: number;
  amount: number;
}

const CHIP_LIST = [
  { val: 5000, label: "5K", img: "/games/xocdia/assets_hd/chip_5k_3d.png?v=bright" },
  { val: 10000, label: "10K", img: "/games/xocdia/assets_hd/chip_10k_3d.png?v=bright" },
  { val: 20000, label: "20K", img: "/games/xocdia/assets_hd/chip_20k_3d.png?v=bright" },
  { val: 50000, label: "50K", img: "/games/xocdia/assets_hd/chip_50k_3d.png?v=bright" },
  { val: 100000, label: "100K", img: "/games/xocdia/assets_hd/chip_100k_3d.png?v=bright" },
  { val: 200000, label: "200K", img: "/games/xocdia/assets_hd/chip_200k_3d.png?v=bright" },
  { val: 500000, label: "500K", img: "/games/xocdia/assets_hd/chip_500k_3d.png?v=bright" },
  { val: 1000000, label: "1M", img: "/games/xocdia/assets_hd/chip_1m_3d.png?v=bright" },
  { val: 5000000, label: "5M", img: "/games/xocdia/assets_hd/chip_5m_3d.png?v=bright" },
  { val: 10000000, label: "10M", img: "/games/xocdia/assets_hd/chip_10m_3d.png?v=bright" },
  { val: 50000000, label: "50M", img: "/games/xocdia/assets_hd/chip_50m_3d.png?v=bright" },
];

const PLAYERS = [
  { id: "p1", name: "Kyoko", balance: "120.4M", left: 160, top: 40, avatar: "/games/xocdia/assets_hd/avatar_1.png" },
  { id: "p2", name: "Huyen_VIP", balance: "2.1M", left: 80, top: 125, avatar: "/games/xocdia/assets_hd/avatar_2.png" },
  { id: "p3", name: "DaiPhat", balance: "50.7M", left: 18, top: 215, avatar: "/games/xocdia/assets_hd/avatar_3.png" },
  { id: "p4", name: "Techweat", balance: "50.7M", left: 14, top: 315, avatar: "/games/xocdia/assets_hd/avatar_4.png" },
  { id: "p5", name: "Cool_Man", balance: "120.4M", left: 770, top: 40, avatar: "/games/xocdia/assets_hd/avatar_5.png" },
  { id: "p6", name: "Lady_V", balance: "2.1M", left: 850, top: 125, avatar: "/games/xocdia/assets_hd/avatar_6.png" },
  { id: "p7", name: "YoungVIP", balance: "50.7M", left: 916, top: 215, avatar: "/games/xocdia/assets_hd/avatar_7.png" },
  { id: "p8", name: "Nsumy", balance: "77.8M", left: 918, top: 315, avatar: "/games/xocdia/assets_hd/avatar_8.png" },
];

// Calibrated safe chip landing bounds inside card felts (prevents spill over borders or covering bottom number bars)
const BET_ZONE_BOUNDS: Record<
  keyof BetState,
  { minX: number; maxX: number; minY: number; maxY: number; defaultX: number; defaultY: number }
> = {
  XOC_DIA_EVEN: { minX: 255, maxX: 375, minY: 130, maxY: 168, defaultX: 310, defaultY: 149 },
  XOC_DIA_ODD: { minX: 650, maxX: 770, minY: 130, maxY: 168, defaultX: 710, defaultY: 149 },
  XOC_DIA_FOUR_RED: { minX: 285, maxX: 335, minY: 298, maxY: 332, defaultX: 310, defaultY: 315 },
  XOC_DIA_FOUR_WHITE: { minX: 412, maxX: 462, minY: 298, maxY: 332, defaultX: 437, defaultY: 315 },
  XOC_DIA_THREE_WHITE: { minX: 555, maxX: 605, minY: 298, maxY: 332, defaultX: 580, defaultY: 315 },
  XOC_DIA_THREE_RED: { minX: 680, maxX: 730, minY: 298, maxY: 332, defaultX: 705, defaultY: 315 },
};

interface ChatMessage {
  id: string;
  sender: string;
  role: "USER" | "BOT" | "DEALER" | "PLAYER";
  text: string;
  time: string;
  avatar?: string;
  isWarning?: boolean;
}

const PROFANITY_PATTERN =
  /\b(dm|dmm|dcm|đm|đmm|đcm|vcl|vl|cl|clm|cặc|cak|cac|buồi|lồn|lon|lồz|đụ|duma|đuma|đụ má|mẹ mày|bố mày|chó đẻ|óc chó|thằng chó|bịp|lừa đảo|đéo|dell|mọe|đjt|djt|địt|dit me|cút mẹ|hãm lồn)\b/i;

const PROFANITY_EXTENDED_PATTERN =
  /(d[\s._-]*m|đ[\s._-]*m|v[\s._-]*c[\s._-]*l|c[\s._-]*ặ[\s._-]*c|l[\s._-]*ồ[\s._-]*n|đ[\s._-]*ụ|đ[\s._-]*ị[\s._-]*t|b[\s._-]*ị[\s._-]*p)/i;

const checkSwearWords = (rawText: string): boolean => {
  const text = rawText.toLowerCase().trim();
  if (PROFANITY_PATTERN.test(text) || PROFANITY_EXTENDED_PATTERN.test(text)) {
    return true;
  }
  const stripped = text.replace(/[\s.\-_*#@!]+/g, "");
  return /(dm|dmm|dcm|đm|đmm|đcm|vcl|clm|cặc|cak|buồi|lồn|lồz|đụmá|duma|đuma|mẹmày|bốmày|chóđẻ|ócchó|thằngchó|bịp|đjt|djt|địtmẹ|ditme|hãmlồn|đéo|dell)/i.test(
    stripped
  );
};

const BOT_TROLL_RESPONSES = [
  "Ê ê {user}, ăn nói có học thức xíu nha đại gia! Bàn này sòng quốc tế 5 sao chứ có phải bến xe đâu. Không được chửi thề, không tao kick m khỏi sới xóc đĩa giờ! 😏🔨",
  "Cái mồm đi chơi hơi xa rồi đó nha {user}! Thua có mấy đồng lẻ mà văng tục, tao kick 1 phát bay về đất liền bán trà đá bây giờ! 🍼😂",
  "Alo cảnh sát văn hóa Genting nghe rõ! Phát hiện {user} đang cay cú. Bình tĩnh nạp thêm tiền gỡ gạc, chửi thề câu nữa là tao sút bay màu khỏi phòng! 🚨🦵",
  "Gắt vậy ba? Em Vy Vy dealer xinh đẹp đang nhìn mà ăn nói thế à {user}? Cấm chửi thề nha, nhảm nhí tao kick ra đảo ngắm cá mập bây giờ! 💅🦈",
  "Troll troll thế thôi chứ chửi thề nữa tao kick thiệt á nha {user}! Bình tĩnh vào tiền gỡ gạc, lịch sự tí coi nè! 😜🚪",
  "Chửi thề là phong độ tụt dốc, gãy cầu ráng chịu nha {user}! Lần này bot tha nhẹ, còn văng tục nữa là ăn vé tiễn vong khỏi bàn cấp tốc! 🛑🤪",
];

const BOT_STRIKE_2_RESPONSES = [
  "CẢNH CÁO LẦN 2 nha {user}! Thẻ vàng thứ 2 rồi đấy, còn văng tục 1 lần nữa là tao kích văng khỏi sới thật đấy, không đùa đâu nha đại ca! 🟨⚠️",
  "Máu dồn lên não rồi à {user}? Ra làm cốc nước cam hạ hỏa đi, chửi thêm chữ nữa là bot bấm nút tiễn vong không hẹn ngày về! 🍊🔥",
];

const QUICK_CHATS = [
  "Bắt Chẵn đi anh em! 🎲",
  "Bẻ sang Lẻ thôi! 🔥",
  "Tứ Đỏ 1 ăn 16 nổ hũ! 💎",
  "Vy Vy xóc son quá em ơi! ❤️",
  "Tất tay cứu nét ván này! 🚀",
  "Cầu đang đẹp, theo thôi! ✨",
];

// Câu trả lời thân thiện khi user chat thường (không chửi thề) — giữ bàn xôm tụ
const BOT_SOCIAL_REPLIES = [
  "Chuẩn luôn {user} ơi! Tay này tôi cũng đang nghiêng về Chẵn nè! 🎲",
  "Haha {user} nói chí phải! Vào nhẹ tay thôi còn gỡ nè! 🔥",
  "Ok {user}, theo kèo của đại gia luôn! Ăn thì khao cả bàn nhé! 😎",
  "Cầu này khó đoán quá {user} ơi, mà nghe theo bạn một tay xem sao! ✨",
  "Uy tín nha {user}! Bàn VIP này toàn cao thủ soi cầu! 💎",
  "Tôi vừa vào 2M theo ý {user} luôn, húp thì chia lộc nhé! 🚀",
  "Chào {user}! Chúc đại gia tay này nổ to rực rỡ nhé! 🥂",
  "Nghe {user} hô mà máu chiến sôi lên rồi, tất tay thôi anh em! ❤️",
];

const DEALER_SOCIAL_REPLIES = [
  "Dạ em chào đại gia {user}! Chúc mình tay này thắng lớn nhé~ ❤️",
  "{user} nói hay quá, em xóc nhẹ tay cho ra cầu đẹp nè! 🎲",
  "Cảm ơn {user} đã khuấy động bàn VIP! Cả nhà vào tiền nhanh kẻo hết giờ nhé~ ✨",
];

// =========================================================
// 105 AUTHENTIC CASINO PSYCHOLOGICAL SHILL / BOT MESSAGES
// =========================================================
// Nhóm 1: Hô cầu / Dụ cược bệt Chẵn (15 câu)
const BOT_CHATS_CHAN = [
  "Cầu này bệt Chẵn 100% rồi, anh em tất tay Chẵn ván này đi!",
  "Nhìn cầu 4 đỏ ván trước là biết ván này còn bệt Chẵn, đè mạnh Chẵn nha ae!",
  "Cầu đang chạy thông Chẵn, ai bẻ là đứt tay, cứ bám Chẵn mà lụm lúa!",
  "Tay này tôi vào 5M Chẵn, ai theo chung thuyền vào bờ nào! 🎲",
  "Không thể lệch đi đâu được, Chẵn chắc nịch như đinh đóng cột!",
  "Chẵn ván này không ra tôi thề xóa game, anh em tin tôi một lần đi!",
  "Tiếng bạc này kết Chẵn quá, làm nhẹ 2M lót dạ nào anh em.",
  "Cầu bệt Chẵn đang thơm, đừng ai dại mà bẻ ngược dòng nha!",
  "Chẵn đi anh em ơi, thần tài đang gõ cửa cửa Chẵn kìa!",
  "Theo Chẵn từ đầu phiên tới giờ bú đẫm, ván này tiếp tục Chẵn!",
  "Ai chung chí hướng Chẵn tay này giơ tay cái coi nào! 🔥",
  "Bệt 3 tay chẵn rồi, lịch sử bàn này bệt ít nhất 6 tay, cứ vả Chẵn thôi!",
  "Chẵn ván này ngon ăn quá, tất tay luôn sợ gì sới!",
  "Vào tiền Chẵn nhanh còn kịp ae ơi, đồng hồ sắp hết giây rồi kìa!",
  "Tự tin vào Chẵn tay này, linh cảm mách bảo ăn to rực rỡ!",
];

// Nhóm 2: Dụ bẻ cầu / Vào tiền Lẻ (15 câu)
const BOT_CHATS_LE = [
  "Bệt Chẵn 4 tay rồi kiểu gì ván này cũng phải bẻ sang Lẻ!",
  "Ai cùng tôi bẻ Lẻ ván này không? Cơ hội ngàn năm có một!",
  "Cầu bệt căng quá rồi, tay này tất tay bẻ Lẻ là ấm no luôn! 🔥",
  "Lẻ chắc chắn nổ ván này, tin tôi đi không lệch đi đâu được đâu!",
  "Đè Lẻ tay này đi cả nhà, vừa soi bảng vị thấy Lẻ quá đẹp!",
  "Bẻ Lẻ cứu nét ván này anh em ơi, khô máu với sới luôn!",
  "Không tin không ra Lẻ, tay này tôi theo 3M Lẻ, anh em vững tin!",
  "Lẻ nổ tưng bừng ván này, ai theo tôi là có bánh chưng nhân thịt!",
  "Nhìn con xúc xắc lắc là biết văng Lẻ rồi, vào lẹ kẻo khóa cược!",
  "Bẻ cầu thành công là đổi đời, dồn hết vốn vào Lẻ nào!",
  "Theo Lẻ đi anh em, ván này cửa Lẻ sáng nhất vịnh Bắc Bộ!",
  "Ai sợ thì đứng nhìn, ai liều thì theo tôi vào Lẻ tay này!",
  "Cầu 1-1 đang chờ sẵn, bẻ sang Lẻ nhịp này là đúng bài!",
  "Cả bàn cùng hô hào vào Lẻ cho xôm tụ nào anh em ơi!",
  "Lẻ ván này mà không ra thì sới này quá ảo, tự tin vào tiền!",
];

// Nhóm 3: Dụ cược vị tỷ lệ cao Tứ Tử 1:16 & 3 Đỏ / 3 Trắng 1:4 (15 câu)
const BOT_CHATS_VI = [
  "Lót nhẹ 100k vào Tứ Đỏ xem nào, 1 ăn 16 nổ một cái là đổi đời! 💎",
  "Nghi ván này về Tứ Trắng lắm nha, lót 50k cầu may anh em ơi!",
  "Đánh Chẵn phải kèm theo Tứ Đỏ và Tứ Trắng bảo hiểm mới chuẩn VIP!",
  "Làm nhẹ quả Ba Đỏ 1 ăn 4, thơm như múi mít cả nhà ơi!",
  "Cầu này dễ nổ Tứ Tử cực kỳ, bỏ vài đồng lót vị biết đâu chiều trúng số!",
  "Vừa nằm mơ thấy 4 hột cùng màu đỏ au, ván này ôm Tứ Đỏ thôi!",
  "Ba Trắng Lẻ tỷ lệ 1 ăn 4 quá thơm, vào 200k lụm 800k uống cà phê!",
  "Hôm qua tôi vừa ăn được quả Tứ Tử 1 ăn 16 ở bàn này, hôm nay lặp lại nè!",
  "Cược vị mới là đỉnh cao xóc đĩa, đánh đều tay Ba Đỏ nhé anh em!",
  "Tứ Đỏ ơi về với anh nào, nổ 1 phát dẫn cả phòng đi bar quẩy luôn! 🥂",
  "Anh em ai bắt Chẵn nhớ thả 50k vào Tứ Tử nhé, không về tiếc hùi hụi!",
  "Tay em Vy Vy xóc thế này dễ chụm hột thành Tứ lắm á nha!",
  "Tứ Trắng ván này tỷ lệ về cực cao, liều ăn nhiều không nói nhiều!",
  "Thả nhẹ vài đồng vào Ba Đỏ, cửa này cầu vị đang báo rầm rộ!",
  "Đã chơi là phải săn hũ Tứ Tử, ván này nổ to cho sới biết tay!",
];

// Nhóm 4: Khoe thắng / Kích thích lòng tham (15 câu)
const BOT_CHATS_WIN_BRAG = [
  "Húp trọn 10M rồi anh em ơi! Cảm ơn sới Genting uy tín nha! 😎🥂",
  "Nghe lời bẻ cầu ăn đậm quá, ấm no luôn cả tuần!",
  "Thông 4 tay liền rồi, hôm nay ngày hoàng đạo của tôi rồi ae ơi! ✨",
  "Tiền về tài khoản ting ting phê quá, tối nay cua hoàng đế thôi!",
  "Sới này đang nhả tiền cực mạnh, anh em tranh thủ vào tiền húp lẹ!",
  "Vừa gỡ lại hết số hôm qua lại còn lãi thêm 5 củ, sướng rơn người! 🚀",
  "Ăn thông từ nãy tới giờ chưa xịt tay nào, phong độ ngút trời!",
  "Ai theo tôi từ đầu phiên giờ chắc ấm túi hết rồi đúng không nào?",
  "Quá uy tín luôn! Cầu chạy chuẩn từng milimet thế này thì giàu to!",
  "Thắng lớn ván này xin phép mời cả bàn ly bia online nhé anh em! 🍻",
  "Húp trọn quả cược vị, tài khoản nhảy số nhìn đã mắt ghê!",
  "Vào tiền dứt khoát là có thưởng, anh em cứ rụt rè là mất phần!",
  "Mới nạp 2 triệu giờ lên gần chục củ rồi, rút bớt ăn mừng thôi!",
  "May quá tay nãy không run tay, tất tay ăn trọn vẹn!",
  "Cứ đà này hôm nay làm con SH mới được, sới đãi quá xá!",
];

// Nhóm 5: Cay cú kêu gọi gấp thếp gỡ gạc (15 câu)
const BOT_CHATS_LOSE_RAGE = [
  "Cay thế nhờ! Vừa bẻ sang thì nó lại bệt, ván này x2 gỡ lại! 😤",
  "Mới sảy chân 1 tay, tay này gấp thếp lấy lại cả vốn lẫn lãi!",
  "Thua keo này ta bày keo khác, anh em vững tay chèo không được nản!",
  "Đang đà son tự nhiên gãy 1 tay, ván này quyết khô máu lấy lại uy danh!",
  "Tiền vẫn trong sới thôi, ván này vào gấp đôi gỡ gạc tức thì!",
  "Chưa hết tiền là chưa hết hy vọng, anh em cùng tôi lội ngược dòng nào!",
  "Cầu này ảo thật chứ, nhưng không sao, tay này tôi bắt thóp sới!",
  "Thua có tí mà xoắn, nạp thêm vào đè bẹp sới ván này!",
  "Gấp thếp là chân ái của xóc đĩa, tay này nâng cược lên lấy lại liền!",
  "Cay cú làm gì anh em, bình tĩnh soi cầu ván này ăn lại gấp ba!",
  "Ván trước coi như nhử mồi, ván này mới là cú đấm quyết định!",
  "Bắt đầu chiến dịch gỡ gạc, ai cùng chí hướng khô máu theo tôi!",
  "Còn thở là còn gỡ, anh em chuẩn bị tiền vào ván mới nào!",
  "Tưởng nuốt được tiền của tôi à, ván này tôi đòi lại cả gốc lẫn lãi!",
  "Chút trắc trở thôi, đại gia Genting không bao giờ bỏ cuộc!",
];

// Nhóm 6: Soi cầu logic & Bình luận bảng vị (15 câu)
const BOT_CHATS_SOI_CAU = [
  "Nhìn bảng soi cầu phía dưới kìa, cầu 1-1 đang chạy chuẩn chỉ luôn!",
  "Cầu nhảy 2 Chẵn 2 Lẻ kinh điển, ván này chuẩn bài về Lẻ nhé ae!",
  "Bảng vị đang nghiêng hẳn về màu đỏ, khả năng cao ván này Chẵn 2 đỏ 2 trắng!",
  "Cầu tam giác đang hình thành ở góc trái, anh em để ý bảng cầu nha!",
  "Lịch sử 20 ván gần nhất Chẵn chiếm 65%, cửa Chẵn đang có lợi thế lớn!",
  "Ai biết soi cầu nhìn cái là hiểu ngay ván này đánh gì rồi, khỏi bàn cãi!",
  "Cầu bệt chưa gãy được đâu, kinh nghiệm 5 năm của tôi không sai đâu!",
  "Đang nhịp cầu chuyền, đánh thuận theo dòng nước là không bao giờ lỗ!",
  "Bảng vị 52 ván hôm nay quá đều, vào tiền theo công thức là lượm lúa!",
  "Cầu bão sắp tới rồi, anh em chuẩn bị tinh thần đón sóng lớn!",
  "Soi kỹ bảng hạt đỏ hạt trắng trước khi hạ cược nhé anh em ơi!",
  "Cầu này gọi là cầu rồng cuốn, ai biết nương theo là ăn đủ!",
  "Không tin thì nhìn lại lịch sử xem, y hệt thế cầu ván này luôn!",
  "Cầu đang đẹp thế này mà không chơi thì phí hoài tuổi thanh xuân!",
  "Bắt theo nhịp lắc của dealer, ván này tỉ lệ Chẵn lên đến 85%!",
];

// Nhóm 7: Tương tác Dealer Vy Vy & Kích hoạt Bot AI Bảo Kê troll (15 câu)
const BOT_CHATS_DEALER_AND_TROLL = [
  "Em Vy Vy nay mặc áo đỏ may mắn quá, tay xóc son ghê gớm! ❤️",
  "Vy Vy cười một cái cho cả bàn tự tin tất tay vào Chẵn nào em ơi!",
  "Dealer xinh gái thế này ngồi ngắm thôi cũng thấy vui rồi~",
  "Nhờ vía em Vy Vy mà nãy giờ tôi ăn thông 3 tay, cảm ơn người đẹp nha!",
  "Bot bảo kê sới nay có phát lộc cho anh em không bot ơi? 😂",
  "Bàn VIP này dealer chuyên nghiệp mà bot bảo kê cũng gắt phết nhờ!",
  "Vy Vy xóc nhẹ tay chút cho hạt nằm êm cửa Lẻ giùm anh nhé! 😉",
  "Có ai thấy dealer Vy Vy hôm nay nhìn cuốn hút lạ thường không?",
  "Sới này vui thật, vừa chơi vừa ngắm người đẹp lại có bot troll vui vẻ!",
  "Đánh thắng ván này nhất định gửi tặng em Vy Vy bó hoa to bự! 🌹",
  "Vcl vừa gãy cầu cay thế nhờ sới ơi! (Thử lòng bot bảo kê tí xem dọa kick không 🤣)",
  "Đm ván này mà không về Chẵn tao thề bỏ ăn tối! (Bot đừng kick tao nha haha)",
  "Bịp thật sự, né cửa của tao miết thế nhờ! 😤",
  "Không khí bàn VIP xôm tụ như hội chợ, chúc anh em ai cũng húp trọn!",
  "Genting mãi đỉnh, vừa nạp tiền là vào xóc đĩa chiến liền tay!",
];

/**
 * Định dạng hệ số thực nhận của server thành chuỗi hiển thị ("1.98", "16").
 *
 * Đặt ở phạm vi MODULE chứ không trong thân component: hàm trong thân component đọc
 * biến của lượt render nên khi được gọi từ `placeBet` (một event handler), React
 * Compiler coi cả thân `placeBet` là mã chạy trong lúc render — và mọi lời gọi
 * `Math.random()` sẵn có ở đó bị báo lỗi `react-hooks/purity`.
 */
const formatOddsMultiplier = (raw: string | undefined): string => {
  if (raw === undefined) return "";
  const n = Number(raw);
  return Number.isFinite(n)
    ? n.toFixed(2).replace(/0+$/, "").replace(/\.$/, "")
    : raw;
};

export const XocDiaLandscapeGame: React.FC = () => {
  // Virtual 1024x507 stage scaling
  const [scale, setScale] = useState(1);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleResize = () => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      const s = Math.min(w / 1024, h / 507);
      setScale(s);
    };
    handleResize();
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  // Game lifecycle states
  const [phase, setPhase] = useState<Phase>("BETTING_OPEN");
  const [timeLeft, setTimeLeft] = useState(15);
  const [roundSeq, setRoundSeq] = useState(1088);
  const [coins, setCoins] = useState<number[]>([1, 1, 0, 0]); // 2 red 2 white
  const [balance, setBalance] = useState<number | null>(null); // VND quy đổi từ số dư thật
  const [usdBalance, setUsdBalance] = useState<number | null>(null); // số dư USD thật trong ví
  const [exchangeRate, setExchangeRate] = useState<number>(25000); // Dynamic exchange rate USD -> VND
  const [exchangeRateFormatted, setExchangeRateFormatted] = useState<string>("1 USD = 25,000 VND");
  const [selectedChip, setSelectedChip] = useState<number>(10000); // 10K default
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [showRules, setShowRules] = useState(false);
  const [showTopWins, setShowTopWins] = useState(false);
  const [lastWinAmount, setLastWinAmount] = useState<number | null>(null);

  // Dynamic Bot configuration from Admin CMS with Realtime Sync & Smart Departure
  const [targetBotCount, setTargetBotCount] = useState<number>(4);
  const [seatedBotIds, setSeatedBotIds] = useState<string[]>(() =>
    PLAYERS.slice(0, 4).map((p) => p.id)
  );
  const [pendingLeaveBotIds, setPendingLeaveBotIds] = useState<string[]>([]);
  const pendingLeaveBotIdsRef = useRef<string[]>([]);
  const targetBotCountRef = useRef<number>(4);
  const seatedBotIdsRef = useRef<string[]>(PLAYERS.slice(0, 4).map((p) => p.id));
  const [botChatEnabled, setBotChatEnabled] = useState<boolean>(true);

  // Keep refs synchronized with state for real-time timer cycles
  useEffect(() => {
    seatedBotIdsRef.current = seatedBotIds;
  }, [seatedBotIds]);

  useEffect(() => {
    pendingLeaveBotIdsRef.current = pendingLeaveBotIds;
  }, [pendingLeaveBotIds]);

  useEffect(() => {
    targetBotCountRef.current = targetBotCount;
  }, [targetBotCount]);

  // Only seated bots participate in visual rendering and chat
  const activeBots = PLAYERS.filter((p) => seatedBotIds.includes(p.id));

  // Intelligent bot synchronization:
  // - If bot count decreases:
  //   + Bot has NO chips on table: leaves immediately!
  //   + Bot HAS chips on table in current round: marked pendingLeave, finishes round & payout, then leaves at settlement!
  // - If bot count increases: new bots enter immediately.
  const syncBotConfig = (newCount: number, chatEnabled?: boolean) => {
    if (typeof chatEnabled === "boolean") {
      setBotChatEnabled(chatEnabled);
    }

    const safeCount = Math.max(1, Math.min(newCount, PLAYERS.length));
    setTargetBotCount(safeCount);
    targetBotCountRef.current = safeCount;

    const targetIds = PLAYERS.slice(0, safeCount).map((p) => p.id);
    const currentSeated = seatedBotIdsRef.current;

    const botsToAdd = targetIds.filter((id) => !currentSeated.includes(id));
    const excessBots = currentSeated.filter((id) => !targetIds.includes(id));

    if (excessBots.length === 0 && botsToAdd.length === 0) {
      return;
    }

    const currentChips = tableChipsRef.current;
    const immediateLeave: string[] = [];
    const pendingLeave: string[] = [];

    excessBots.forEach((botId) => {
      const hasBetInCurrentRound = currentChips.some((c) => c.playerId === botId);
      if (hasBetInCurrentRound) {
        pendingLeave.push(botId);
      } else {
        immediateLeave.push(botId);
      }
    });

    setSeatedBotIds((prev) => {
      const filtered = prev.filter((id) => !immediateLeave.includes(id));
      botsToAdd.forEach((id) => {
        if (!filtered.includes(id)) filtered.push(id);
      });
      const updated = PLAYERS.filter((p) => filtered.includes(p.id)).map((p) => p.id);
      seatedBotIdsRef.current = updated;
      return updated;
    });

    setPendingLeaveBotIds((prev) => {
      const merged = Array.from(
        new Set([...prev.filter((id) => !targetIds.includes(id)), ...pendingLeave])
      );
      pendingLeaveBotIdsRef.current = merged;
      return merged;
    });
  };

  // Floating luxury in-game toast notification
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const toastTimerRef = useRef<NodeJS.Timeout | null>(null);

  const showToast = (msg: string) => {
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    setToastMsg(msg);
    toastTimerRef.current = setTimeout(() => {
      setToastMsg(null);
    }, 2600);
  };

  // Intelligent NPC Dealer dialogue system
  const [dealerSpeech, setDealerSpeech] = useState<{ id: number; text: string } | null>({
    id: 1,
    text: "Chào mừng các đại gia đến với Genting! Chúc anh em hôm nay rực rỡ nhé~ ❤️",
  });
  const speechTimerRef = useRef<NodeJS.Timeout | null>(null);
  const nextSpeechId = useRef(2);

  const speakDealer = (text: string, durationMs: number = 4200) => {
    if (speechTimerRef.current) clearTimeout(speechTimerRef.current);
    const id = nextSpeechId.current++;
    setDealerSpeech({ id, text });
    speechTimerRef.current = setTimeout(() => {
      setDealerSpeech(null);
    }, durationMs);
  };

  const dealerClickQuotes = [
    "Anh ngắm em hoài thế, tập trung vào tiền thắng lớn rồi dẫn em đi ăn tối nè! 😉❤️",
    "Em là Vy Vy, dealer độc quyền của đại gia Genting! Anh thích Chẵn hay Lẻ để em chiều nào? ✨",
    "Tay em xóc nãy giờ son lắm á, anh theo cửa nào là cửa đó nổ tưng bừng luôn! 🎲🔥",
    "Đại gia Tôi hôm nay phong độ quá! Ván này nổ to là em chúc mừng đầu tiên nha~ 🥂",
    "Em đứng đây để mang may mắn cho anh đó, tự tin vào tiền rinh thưởng khủng nhé! 💎",
  ];
  const clickQuoteIdx = useRef(0);

  const handleDealerClick = () => {
    playSound("bell");
    const quote = dealerClickQuotes[clickQuoteIdx.current % dealerClickQuotes.length];
    clickQuoteIdx.current++;
    speakDealer(quote, 4500);
  };

  // If selected chip becomes unaffordable due to placing bets, auto-downgrade to highest affordable chip
  useEffect(() => {
    // Số dư chưa đọc được thì KHÔNG hạ chip: `null` là "chưa biết", không phải "hết tiền".
    if (balance !== null && balance < selectedChip) {
      const affordable = CHIP_LIST.filter((c) => c.val <= balance);
      if (affordable.length > 0) {
        setSelectedChip(affordable[affordable.length - 1].val);
      }
    }
  }, [balance, selectedChip]);

  // Real Backend Data & Session Bet Logs for Modals
  interface SessionBetLog {
    id: number;
    roundSeq: number;
    /** Vòng SERVER của lần đặt này — khoá để khớp với `payout` thật khi chốt sổ. */
    roundId: string | null;
    /** Cửa đã đặt, dùng để khớp phiếu này với bản ghi cược tương ứng ở server. */
    betType: keyof BetState;
    zoneName: string;
    stake: number;
    payout: number;
    won: boolean;
    time: string;
  }

  const [topWinsTab, setTopWinsTab] = useState<"LEADERBOARD" | "MY_BETS" | "STATS">("LEADERBOARD");
  const [realUsername, setRealUsername] = useState<string>("VIP Tôi");
  const [backendBets, setBackendBets] = useState<PlayerBet[]>([]);
  const [sessionBetLogs, setSessionBetLogs] = useState<SessionBetLog[]>([]);
  const [isLoadingBackend, setIsLoadingBackend] = useState<boolean>(false);
  const [sessionTotalWon, setSessionTotalWon] = useState<number>(0);

  const fetchBackendData = async () => {
    setIsLoadingBackend(true);
    try {
      const [uData, wData, rateData, cfgData, jackpotData] = await Promise.allSettled([
        me(),
        walletMe(),
        getExchangeRate(),
        getXocDiaConfig(),
        getXocDiaJackpot(),
      ]);

      if (cfgData.status === "fulfilled" && cfgData.value) {
        if (typeof cfgData.value.botCount === "number") {
          syncBotConfig(cfgData.value.botCount, cfgData.value.botChatEnabled);
        }
      }

      if (jackpotData.status === "fulfilled" && jackpotData.value) {
        const j = jackpotData.value;
        jackpotCfgRef.current = {
          triggerMode: j.triggerMode || "AUTO",
          autoRate: typeof j.autoRate === "number" ? j.autoRate : 0.001,
          targetDoor: j.targetDoor || "RANDOM",
          pool: typeof j.pool === "number" ? j.pool : 295313767,
          minPool: typeof j.minPool === "number" ? j.minPool : 100000000,
        };
        setJackpot(jackpotCfgRef.current.pool);
      }

      let currentRate = exchangeRate;
      if (rateData.status === "fulfilled" && rateData.value?.rate) {
        currentRate = rateData.value.rate;
        setExchangeRate(currentRate);
        if (rateData.value.formattedRate) {
          setExchangeRateFormatted(rateData.value.formattedRate);
        }
      }

      if (uData.status === "fulfilled" && uData.value?.username) {
        setRealUsername(uData.value.username);
        realUsernameRef.current = uData.value.username;
      }

      if (wData.status === "fulfilled" && wData.value?.balance) {
        applyUsdBalance(parseFloat(wData.value.balance));
      }

      const bRes = await betsHistory(undefined, 0, 25).catch(() => null);
      if (bRes && Array.isArray(bRes.content)) {
        setBackendBets(bRes.content);
      }
    } catch (err) {
      console.warn("Backend fetch in xocdia modal:", err);
    } finally {
      setIsLoadingBackend(false);
    }
  };

  useEffect(() => {
    fetchBackendData();

    // Periodic Real-time Sync of Bot + Jackpot configuration from Admin CMS (every 3s)
    const interval = setInterval(async () => {
      try {
        const cfg = await getXocDiaConfig();
        if (cfg && typeof cfg.botCount === "number") {
          syncBotConfig(cfg.botCount, cfg.botChatEnabled);
        }
      } catch (err) {
        // Silently catch network hiccups
      }
      try {
        const j = await getXocDiaJackpot();
        if (j) {
          jackpotCfgRef.current = {
            triggerMode: j.triggerMode || "AUTO",
            autoRate: typeof j.autoRate === "number" ? j.autoRate : 0.001,
            targetDoor: j.targetDoor || "RANDOM",
            pool: typeof j.pool === "number" ? j.pool : jackpotCfgRef.current.pool,
            minPool: typeof j.minPool === "number" ? j.minPool : jackpotCfgRef.current.minPool,
          };
          const prevWon = lastWonRef.current || "";
          const curWon = j.lastWon || "";
          const prevPool = poolRef.current;
          const curPool = typeof j.pool === "number" ? j.pool : prevPool;
          poolRef.current = curPool;
          setJackpot(curPool);
          // Phat hien van no THAT tu server: lastWon doi (ca ban cung poll nen cung thay)
          if (curWon && curWon !== prevWon) {
            lastWonRef.current = curWon;
            const parsed = parseLastWon(curWon);
            const winAmount = parsed?.amount || Math.max(0, prevPool - curPool);
            const winnerName = parsed?.winnerName || (j as unknown as { winner?: string }).winner || "";
            const roundSeq = parsed?.roundSeq || 0;
            if (winAmount > 0 && winnerName) {
              const diceVal = DOOR_TO_DICE_REAL[j.targetDoor] || 6;
              const quad = [diceVal, diceVal, diceVal, diceVal];
              setJackpotDice(quad);
              const isMine =
                winnerName.toLowerCase() === (realUsernameRef.current || "").toLowerCase();
              setJackpotWin({
                amount: winAmount,
                door: DOOR_LABEL_REAL[j.targetDoor] || j.targetDoor,
                dice: quad,
                winnerName,
                roundSeq,
                isMine,
              });
              playSound("win");
              if (isMine) {
                // Minh trung: refresh vi that tu server de khop so du
                walletMe()
                  .then((w) => {
                    if (w?.balance) applyUsdBalance(parseFloat(w.balance));
                  })
                  .catch(() => {});
                setSessionTotalWon((prev) => prev + winAmount);
                speakDealer(
                  `NỔ HŨ JACKPOT! Tứ Quý ${diceVal}-${diceVal}-${diceVal}-${diceVal} — Chúc mừng ${winnerName} húp trọn ${Math.round(winAmount / 1000).toLocaleString()}K! Quá đỉnh luôn!!`,
                  6000
                );
              } else {
                speakDealer(
                  `NỔ HŨ JACKPOT! ${winnerName} vừa húp ${Math.round(winAmount / 1000).toLocaleString()}K với Tứ Quý ${diceVal}! Chúc mừng đại gia!`,
                  6000
                );
              }
              setTimeout(() => setJackpotWin(null), 8000);
            }
          } else if (!prevWon && curWon) {
            lastWonRef.current = curWon;
          }
        }
      } catch (err) {
        // Silently catch network hiccups
      }
    }, 3000);

    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (showTopWins) {
      fetchBackendData();
    }
  }, [showTopWins]);

  // =========================================================
  // IN-GAME LIVE CHAT ROOM & TROLL AI BOT MODERATOR
  // =========================================================
  const [showChat, setShowChat] = useState<boolean>(false);
  const [unreadChatCount, setUnreadChatCount] = useState<number>(0);
  const [chatInput, setChatInput] = useState<string>("");
  const [isMutedByBot, setIsMutedByBot] = useState<boolean>(false);
  const [muteSecondsLeft, setMuteSecondsLeft] = useState<number>(0);
  const swearCountRef = useRef<number>(0);
  const chatEndRef = useRef<HTMLDivElement | null>(null);

  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([
    {
      id: "m-init-1",
      sender: "DEALER VY VY",
      role: "DEALER",
      text: "Chào mừng các đại gia VIP đến với sới xóc đĩa Genting! Chúc anh em hôm nay rực rỡ nhé~ ❤️",
      time: "20:50",
    },
    {
      id: "m-init-2",
      sender: "AI BẢO KÊ GENTING",
      role: "BOT",
      text: "Hệ thống AI Bảo Kê sới đang trực 24/7! Anh em chơi đẹp lịch sự, ai văng tục chửi thề bot kick thẳng tay ra đảo nhé! 😎🛡️",
      time: "20:51",
    },
    {
      id: "m-init-3",
      sender: "Kyoko",
      role: "PLAYER",
      avatar: "/games/xocdia/assets_hd/avatar_1.png",
      text: "Cầu Chẵn đang bệt đẹp quá anh em ơi! Tay này theo tiếp thôi! 🎲",
      time: "20:52",
    },
  ]);

  // Auto-scroll chat to latest message
  useEffect(() => {
    if (showChat) {
      chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [chatMessages, showChat]);

  // Mute countdown timer (temporary kick / lockout for swearing)
  useEffect(() => {
    if (!isMutedByBot || muteSecondsLeft <= 0) return;
    const timer = setInterval(() => {
      setMuteSecondsLeft((prev) => {
        if (prev <= 1) {
          setIsMutedByBot(false);
          swearCountRef.current = 0;
          const now = new Date();
          const timeStr = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
          setChatMessages((msgs) => [
            ...msgs,
            {
              id: `bot-unmute-${Date.now()}`,
              sender: "AI BẢO KÊ GENTING",
              role: "BOT",
              text: `Chào mừng đại gia ${realUsername} đã hết hạn tù treo sám hối! Lần này ăn nói cho đàng hoàng nghe chưa, chửi nữa tao kick vĩnh viễn không tiễn đấy! 😎🤝`,
              time: timeStr,
            },
          ]);
          playSound("bot");
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [isMutedByBot, muteSecondsLeft, realUsername]);

  // Periodic ambient chat simulation from active seated bots & dealer (Phase-aware 105 messages)
  useEffect(() => {
    if (!botChatEnabled || activeBots.length === 0) return;

    const ambientTimer = setInterval(() => {
      const now = new Date();
      const timeStr = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;

      // Pick a random bot from ONLY active seated bots
      const speakerBot = activeBots[Math.floor(Math.random() * activeBots.length)];
      if (!speakerBot) return;

      // 12% chance a bot gets cheeky / swears mildly and Bot AI reprimands them
      const isCheeky = Math.random() < 0.12;
      if (isCheeky) {
        const cheekyQuotes = [
          "Vcl vừa gãy cầu cay thế nhờ sới ơi! 😭",
          "Bịp thật sự, né cửa của tao miết thế nhờ! 😤",
          "Đm ván này mà không về Lẻ tao thề cút khỏi sới! 🤬",
          "Cay dái vãi chưởng, vừa bẻ cái nó về chẵn luôn!",
          "Sới lừa đảo à, quay xe gắt thế ae!",
        ];
        const quote = cheekyQuotes[Math.floor(Math.random() * cheekyQuotes.length)];

        setChatMessages((prev) => [
          ...prev.slice(-35),
          {
            id: `amb-${Date.now()}-1`,
            sender: speakerBot.name,
            role: "PLAYER",
            avatar: speakerBot.avatar,
            text: quote,
            time: timeStr,
          },
        ]);

        // Bot AI reprimands the ambient player
        setTimeout(() => {
          const botTime = `${String(new Date().getHours()).padStart(2, "0")}:${String(new Date().getMinutes()).padStart(2, "0")}`;
          setChatMessages((prev) => [
            ...prev,
            {
              id: `amb-bot-${Date.now()}`,
              sender: "AI BẢO KÊ GENTING",
              role: "BOT",
              text: `Ê ${speakerBot.name}, ăn nói lịch sự lên nha mạy! Bàn VIP 5 sao chứ không phải bến xe đâu, chửi nữa tao kick bay màu ra đảo bây giờ! 🤖🔨`,
              time: botTime,
              isWarning: true,
            },
          ]);
          playSound("bot");
        }, 750);
      } else {
        // Phase-Aware psychological message selection
        let msgText = "";
        if (phase === "BETTING_OPEN") {
          const roll = Math.random();
          if (roll < 0.35) {
            msgText = BOT_CHATS_CHAN[Math.floor(Math.random() * BOT_CHATS_CHAN.length)];
          } else if (roll < 0.68) {
            msgText = BOT_CHATS_LE[Math.floor(Math.random() * BOT_CHATS_LE.length)];
          } else if (roll < 0.86) {
            msgText = BOT_CHATS_VI[Math.floor(Math.random() * BOT_CHATS_VI.length)];
          } else {
            msgText = BOT_CHATS_SOI_CAU[Math.floor(Math.random() * BOT_CHATS_SOI_CAU.length)];
          }
        } else {
          // RESULT or SETTLE
          const roll = Math.random();
          if (roll < 0.50) {
            msgText = BOT_CHATS_WIN_BRAG[Math.floor(Math.random() * BOT_CHATS_WIN_BRAG.length)];
          } else if (roll < 0.85) {
            msgText = BOT_CHATS_LOSE_RAGE[Math.floor(Math.random() * BOT_CHATS_LOSE_RAGE.length)];
          } else {
            msgText = BOT_CHATS_DEALER_AND_TROLL[Math.floor(Math.random() * 10)];
          }
        }

        setChatMessages((prev) => [
          ...prev.slice(-35),
          {
            id: `amb-${Date.now()}`,
            sender: speakerBot.name,
            role: "PLAYER",
            avatar: speakerBot.avatar,
            text: msgText,
            time: timeStr,
          },
        ]);
      }

      setShowChat((isOpen) => {
        if (!isOpen) {
          setUnreadChatCount((cnt) => Math.min(cnt + 1, 99));
        }
        return isOpen;
      });
    }, 11000); // 11s interval for lively conversation

    return () => clearInterval(ambientTimer);
  }, [botChatEnabled, activeBots, phase]);

  // Send message handler with Vietnamese swear word detection & Troll Bot
  const handleSendMessage = (textToSend: string) => {
    const trimmed = textToSend.trim();
    if (!trimmed || isMutedByBot) return;

    const now = new Date();
    const timeStr = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;

    const newMsg: ChatMessage = {
      id: `user-${Date.now()}`,
      sender: realUsername || "Tôi (VIP)",
      role: "USER",
      text: trimmed,
      time: timeStr,
    };

    setChatMessages((prev) => [...prev.slice(-35), newMsg]);
    setChatInput("");
    playSound("chat");

    // Check swear words
    const isSwearing = checkSwearWords(trimmed);
    if (isSwearing) {
      swearCountRef.current += 1;
      const strike = swearCountRef.current;

      setTimeout(() => {
        const botNow = new Date();
        const botTime = `${String(botNow.getHours()).padStart(2, "0")}:${String(botNow.getMinutes()).padStart(2, "0")}`;

        if (strike === 1) {
          const template = BOT_TROLL_RESPONSES[Math.floor(Math.random() * BOT_TROLL_RESPONSES.length)];
          const reply = template.replace(/{user}/g, realUsername);
          setChatMessages((prev) => [
            ...prev,
            {
              id: `bot-strike1-${Date.now()}`,
              sender: "AI BẢO KÊ GENTING",
              role: "BOT",
              text: reply,
              time: botTime,
              isWarning: true,
            },
          ]);
          playSound("bot");
        } else if (strike === 2) {
          const template = BOT_STRIKE_2_RESPONSES[Math.floor(Math.random() * BOT_STRIKE_2_RESPONSES.length)];
          const reply = template.replace(/{user}/g, realUsername);
          setChatMessages((prev) => [
            ...prev,
            {
              id: `bot-strike2-${Date.now()}`,
              sender: "AI BẢO KÊ GENTING",
              role: "BOT",
              text: reply,
              time: botTime,
              isWarning: true,
            },
          ]);
          playSound("bot");
        } else {
          // Strike 3: Temporarily kick / mute for 10 seconds
          const kickNotice = `🛑 [THẺ ĐỎ - KICK TẠM THỜI] ${realUsername} khẩu nghiệp quá nặng! Bot sút vào phòng sám hối 10 giây để hạ hỏa! Hết 10s mới cho gáy tiếp! ⛔🚪💨`;
          setChatMessages((prev) => [
            ...prev,
            {
              id: `bot-strike3-${Date.now()}`,
              sender: "AI BẢO KÊ GENTING",
              role: "BOT",
              text: kickNotice,
              time: botTime,
              isWarning: true,
            },
          ]);
          playSound("bot");
          setIsMutedByBot(true);
          setMuteSecondsLeft(10);
        }
      }, 450);
    } else {
      // Chat thường: bot / dealer trả lời ngẫu nhiên cho xôm bàn (không im lặng)
      const myName = realUsername || "đại gia";
      setTimeout(() => {
        const botNow = new Date();
        const botTime = `${String(botNow.getHours()).padStart(2, "0")}:${String(botNow.getMinutes()).padStart(2, "0")}`;
        const isDealerReply = Math.random() < 0.25;
        if (isDealerReply) {
          const template =
            DEALER_SOCIAL_REPLIES[Math.floor(Math.random() * DEALER_SOCIAL_REPLIES.length)];
          setChatMessages((prev) => [
            ...prev,
            {
              id: `dealer-reply-${Date.now()}`,
              sender: "DEALER VY VY",
              role: "DEALER",
              text: template.replace(/{user}/g, myName),
              time: botTime,
            },
          ]);
        } else {
          const pool = activeBots.length > 0 ? activeBots : PLAYERS;
          const speaker = pool[Math.floor(Math.random() * pool.length)];
          const template =
            BOT_SOCIAL_REPLIES[Math.floor(Math.random() * BOT_SOCIAL_REPLIES.length)];
          setChatMessages((prev) => [
            ...prev,
            {
              id: `social-reply-${Date.now()}`,
              sender: speaker.name,
              role: "PLAYER",
              avatar: speaker.avatar,
              text: template.replace(/{user}/g, myName),
              time: botTime,
            },
          ]);
        }
        playSound("bot");
      }, 900 + Math.random() * 900);
    }
  };

  // Jackpot live ticker + RubyDice Tu Quy (KET QUA THAT TU SERVER)
  const [jackpot, setJackpot] = useState(295313767);
  const [jackpotDice, setJackpotDice] = useState<number[]>([6, 6, 6, 6]);
  const [jackpotWin, setJackpotWin] = useState<{
    amount: number;
    door: string;
    dice: number[];
    winnerName: string;
    roundSeq: number;
    isMine: boolean;
  } | null>(null);
  const jackpotCfgRef = useRef<{ triggerMode: string; autoRate: number; targetDoor: string; pool: number; minPool: number }>({
    triggerMode: "AUTO",
    autoRate: 0.001,
    targetDoor: "RANDOM",
    pool: 295313767,
    minPool: 100000000,
  });
  // lastWon dang "username +123456 (van 42)" — ca ban cung poll, cung thay no
  const lastWonRef = useRef<string>("");
  const poolRef = useRef<number>(295313767);
  const realUsernameRef = useRef<string>("VIP Tôi");
  const DOOR_TO_DICE_REAL: Record<string, number> = {
    CHAN: 6, LE: 1, FOUR_RED: 2, FOUR_WHITE: 4, THREE_WHITE: 3, THREE_RED: 5,
  };
  const DOOR_LABEL_REAL: Record<string, string> = {
    CHAN: "CHẴN", LE: "LẺ", FOUR_RED: "4 ĐỎ", FOUR_WHITE: "4 TRẮNG",
    THREE_WHITE: "3 TRẮNG 1 ĐỎ", THREE_RED: "3 ĐỎ 1 TRẮNG",
  };
  const parseLastWon = (raw: string): { winnerName: string; amount: number; roundSeq: number } | null => {
    if (!raw) return null;
    const m = raw.match(/(.+?)\s*\+([0-9][0-9.,]*)\s*\(van\s*([0-9]+)\)/i);
    if (m) {
      return {
        winnerName: m[1].trim(),
        amount: parseInt(m[2].replace(/[^0-9]/g, ""), 10) || 0,
        roundSeq: parseInt(m[3], 10) || 0,
      };
    }
    return null;
  };

  // Total server bets
  const [serverBets, setServerBets] = useState({
    even: 476.61,
    odd: 476.51,
    fourRed: 3.44,
    fourWhite: 2.26,
    threeWhite: 4.36,
    threeRed: 5.56,
  });

  // User bets this round
  const [bets, setBets] = useState<BetState>({
    XOC_DIA_EVEN: 0,
    XOC_DIA_ODD: 0,
    XOC_DIA_FOUR_RED: 0,
    XOC_DIA_FOUR_WHITE: 0,
    XOC_DIA_THREE_RED: 0,
    XOC_DIA_THREE_WHITE: 0,
  });

  // Flying chips & Table resting chips & Payout return chips queue
  const [flyingChips, setFlyingChips] = useState<FlyingChip[]>([]);
  const [tableChips, setTableChips] = useState<TableChip[]>([]);
  const [returnChips, setReturnChips] = useState<ReturnChip[]>([]);
  const [winningPlayerIds, setWinningPlayerIds] = useState<string[]>([]);
  const [payoutBursts, setPayoutBursts] = useState<PayoutBurst[]>([]);
  const nextChipId = useRef(1);
  // Real-money wiring (vi that): table/round/seq de goi placeBet server.
  const [xocTableId, setXocTableId] = useState<string | null>(null);
  const xocTableIdRef = useRef<string | null>(null);
  const serverRoundIdRef = useRef<string | null>(null);
  const betSeqRef = useRef<number>(0);
  const placingRef = useRef<boolean>(false);

  // ===== Trạng thái vòng đời ván lấy từ SERVER =====
  /** `phase` bản ref, để vòng poll đọc được giá trị mới nhất mà không cần vào deps. */
  const phaseRef = useRef<Phase>("BETTING_OPEN");
  /** Tỷ lệ cược hiệu lực của CHÍNH người này ở bàn đang chơi. */
  const [serverOdds, setServerOdds] = useState<TableOdds | null>(null);
  /** Mốc kết thúc pha hiện tại, theo đồng hồ SERVER (epoch ms). */
  const phaseEndsAtRef = useRef<number | null>(null);
  /** serverTime − Date.now(). Bù lệch đồng hồ máy người dùng khi đếm ngược. */
  const serverClockOffsetRef = useRef<number>(0);
  /** roundId đã chạy animation mở bát — tránh chạy lại mỗi nhịp poll 1 giây. */
  const revealedRoundIdRef = useRef<string | null>(null);
  /** roundId đã chốt sổ (cộng tiền thắng) — tránh cộng trùng khi WS và REST cùng về. */
  const settledRoundIdsRef = useRef<Set<string>>(new Set());
  /** Mốc serverTime của lần áp số dư gần nhất — chặn gói tới trễ làm nhảy lùi số dư. */
  const lastBalanceAtRef = useRef<number>(0);
  /** False sau lần đồng bộ vòng đầu tiên — xem `applyServerRound`. */
  const firstSyncRef = useRef<boolean>(true);

  /** Thời điểm hiện tại theo đồng hồ server (ước lượng từ lần đồng bộ gần nhất). */
  const serverNowMs = () => Date.now() + serverClockOffsetRef.current;

  /**
   * Áp số dư USD THẬT đọc từ server; VND chỉ là bản quy đổi để hiển thị.
   *
   * Không có nhánh nào tự cộng/trừ ở đây: mọi thay đổi tiền đều đã xảy ra ở ví server, và
   * con số truyền vào là số dư SAU giao dịch do server trả về.
   *
   * `serverTime` dùng để bỏ qua gói cũ: đường HTTP (`/wallet/me`) và đường WebSocket không
   * bảo đảm thứ tự, gói cũ về sau mà vẫn áp thì số dư trên màn hình nhảy lùi.
   */
  const applyUsdBalance = (usdVal: number, serverTime?: string | null) => {
    if (!Number.isFinite(usdVal) || usdVal < 0) return;
    if (serverTime) {
      const at = Date.parse(serverTime);
      if (!Number.isNaN(at)) {
        if (at < lastBalanceAtRef.current) return;
        lastBalanceAtRef.current = at;
      }
    }
    // Đọc tỷ giá từ ref TẠI THỜI ĐIỂM quy đổi, không dùng biến chụp trong closure cũ.
    const rate = exchangeRateRef.current;
    setUsdBalance(usdVal);
    setBalance(rate > 0 ? Math.round(usdVal * rate) : null);
  };
  // Còn sống hay đã unmount — cùng khuôn ở `bet/detail/page.tsx`, để không `setState`
  // sau khi màn đã gỡ.
  const aliveRef = useRef(true);
  useEffect(() => {
    return () => {
      aliveRef.current = false;
    };
  }, []);

  /** Tải tỷ lệ hiệu lực của CHÍNH người này ở một bàn. Xem khối chú thích bên dưới. */
  const loadOdds = useCallback(async (tableId: string) => {
    try {
      const data = await tableOdds(tableId);
      if (aliveRef.current) setServerOdds(data);
    } catch {
      // Không tải được thì rơi về bảng mặc định cho phần hiển thị. Không có phép tính
      // tiền nào dựa vào con số này.
    }
  }, []);

  // Giai quyet table XOC_DIA + round hien tai de co roundId that cho placeBet.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const tables = await gameTables();
        const xoc = tables.find((t) => t.gameType === "XOC_DIA" && t.status === "ACTIVE") || tables.find((t) => t.gameType === "XOC_DIA");
        if (!xoc || cancelled) return;
        xocTableIdRef.current = xoc.id;
        setXocTableId(xoc.id);
        // Tỷ lệ cược tải ngay tại đây chứ không bằng một effect riêng theo `xocTableId`:
        // lời gọi trần trong thân effect làm React Compiler báo `set-state-in-effect`
        // (nó không nhìn xuyên qua ranh giới `await` của `loadOdds`).
        void loadOdds(xoc.id);
        try {
          const round = await currentRound(xoc.id);
          if (!cancelled && round?.roundId) serverRoundIdRef.current = round.roundId;
        } catch {
          // Giua 2 vong server tra 404 ROUND_NOT_FOUND: giu round local, se thu lai khi dat chip.
        }
      } catch {
        // Khong token / mat mang: giu che do visual, khong block animation.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loadOdds]);
  // Reset seq moi vong visual (seq la khoa chong trung trong 1 round server).

  // ===== Tỷ lệ cược THẬT của chính người này ở bàn đang chơi =====
  // Mỗi người chơi có thể được admin đặt tỷ lệ riêng, nên con số hiện trên màn hình chỉ
  // đúng khi lấy từ server. Đây cũng là lý do không được tự nhân odds ở client: ví cộng
  // đúng theo tỷ lệ riêng còn màn hình báo theo tỷ lệ chung là lệch ngay.
  //
  // `loadOdds` khai báo ở khối khởi động phía trên (cần cho lần tải đầu); chỗ này chỉ
  // còn nhánh admin sửa tỷ lệ đẩy xuống.

  // Admin sửa tỷ lệ -> server đẩy xuống; chỉ tải lại nếu đúng bàn đang chơi.
  useEffect(() => {
    const handleOddsUpdated = (e: Event) => {
      const detail = (e as CustomEvent<{ tableId?: string }>).detail;
      if (detail?.tableId && detail.tableId === xocTableIdRef.current) {
        void loadOdds(detail.tableId);
      }
    };
    window.addEventListener("game_odds_updated", handleOddsUpdated);
    return () => window.removeEventListener("game_odds_updated", handleOddsUpdated);
  }, [loadOdds]);

  /**
   * Nhãn "1 ăn X" của từng cửa, lấy từ tỷ lệ hiệu lực của server.
   *
   * `multiplier` là hệ số THỰC NHẬN đã gồm tiền gốc (odds + 1) — không tự cộng 1 ở đây.
   */
  const oddsByType = new Map(
    mergeServerOdds("XOC_DIA", serverOdds?.options ?? null).map((o) => [o.betType, o.multiplier])
  );
  const oddsText = (zone: keyof BetState): string => formatOddsMultiplier(oddsByType.get(zone));
  /** Phần đuôi "— 1 ăn X" cho tooltip; rỗng khi chưa có tỷ lệ để tránh hiện "1 ăn ". */
  const oddsHint = (zone: keyof BetState): string => {
    const text = oddsText(zone);
    return text ? ` — 1 ăn ${text}` : "";
  };

  /**
   * Bản dựng sẵn của `oddsText` cho `placeBet` đọc lúc bấm cược.
   *
   * `placeBet` KHÔNG được gọi `oddsText`: hàm đó là closure của lượt render, và React
   * Compiler sẽ coi lời gọi đó là mã render rồi báo lỗi cho những `Math.random()` sẵn
   * có trong `placeBet`. Đọc ref là đường đi hợp lệ — cùng cách `balanceRef` /
   * `exchangeRateRef` đang dùng.
   */
  const oddsTextRef = useRef<Record<string, string>>({});
  useEffect(() => {
    const texts: Record<string, string> = {};
    for (const o of mergeServerOdds("XOC_DIA", serverOdds?.options ?? null)) {
      texts[o.betType] = formatOddsMultiplier(o.multiplier);
    }
    oddsTextRef.current = texts;
  }, [serverOdds]);

  // Synchronized refs to avoid stale closure during payout and timers
  const tableChipsRef = useRef<TableChip[]>([]);
  const betsRef = useRef<BetState>(bets);

  useEffect(() => {
    tableChipsRef.current = tableChips;
  }, [tableChips]);

  useEffect(() => {
    betsRef.current = bets;
  }, [bets]);

  // History for Bảng Soi Cầu
  // History for Bảng Soi Cầu (52 rich initial rounds to display authentic casino streaks/cầu)
  const [history, setHistory] = useState<RoundHistory[]>([
    { seq: 1036, redCount: 2, isEven: true },
    { seq: 1037, redCount: 2, isEven: true },
    { seq: 1038, redCount: 4, isEven: true },
    { seq: 1039, redCount: 2, isEven: true },
    { seq: 1040, redCount: 3, isEven: false },
    { seq: 1041, redCount: 2, isEven: true },
    { seq: 1042, redCount: 1, isEven: false },
    { seq: 1043, redCount: 0, isEven: true },
    { seq: 1044, redCount: 3, isEven: false },
    { seq: 1045, redCount: 1, isEven: false },
    { seq: 1046, redCount: 3, isEven: false },
    { seq: 1047, redCount: 2, isEven: true },
    { seq: 1048, redCount: 2, isEven: true },
    { seq: 1049, redCount: 1, isEven: false },
    { seq: 1050, redCount: 3, isEven: false },
    { seq: 1051, redCount: 2, isEven: true },
    { seq: 1052, redCount: 2, isEven: true },
    { seq: 1053, redCount: 4, isEven: true },
    { seq: 1054, redCount: 2, isEven: true },
    { seq: 1055, redCount: 0, isEven: true },
    { seq: 1056, redCount: 3, isEven: false },
    { seq: 1057, redCount: 2, isEven: true },
    { seq: 1058, redCount: 1, isEven: false },
    { seq: 1059, redCount: 2, isEven: true },
    { seq: 1060, redCount: 3, isEven: false },
    { seq: 1061, redCount: 1, isEven: false },
    { seq: 1062, redCount: 2, isEven: true },
    { seq: 1063, redCount: 4, isEven: true },
    { seq: 1064, redCount: 3, isEven: false },
    { seq: 1065, redCount: 2, isEven: true },
    { seq: 1066, redCount: 2, isEven: true },
    { seq: 1067, redCount: 1, isEven: false },
    { seq: 1068, redCount: 3, isEven: false },
    { seq: 1069, redCount: 1, isEven: false },
    { seq: 1070, redCount: 2, isEven: true },
    { seq: 1071, redCount: 3, isEven: false },
    { seq: 1072, redCount: 2, isEven: true },
    { seq: 1073, redCount: 0, isEven: true },
    { seq: 1074, redCount: 1, isEven: false },
    { seq: 1075, redCount: 2, isEven: true },
    { seq: 1076, redCount: 4, isEven: true },
    { seq: 1077, redCount: 3, isEven: false },
    { seq: 1078, redCount: 1, isEven: false },
    { seq: 1079, redCount: 2, isEven: true },
    { seq: 1080, redCount: 2, isEven: true },
    { seq: 1081, redCount: 3, isEven: false },
    { seq: 1082, redCount: 0, isEven: true },
    { seq: 1083, redCount: 2, isEven: true },
    { seq: 1084, redCount: 1, isEven: false },
    { seq: 1085, redCount: 4, isEven: true },
    { seq: 1086, redCount: 2, isEven: true },
    { seq: 1087, redCount: 3, isEven: false },
  ]);

  // Bản ref của `history` cho các closure sống lâu (lời thoại dealer trong setTimeout):
  // closure chụp biến state sẽ đọc mãi giá trị của lần render đầu.
  const historyRef = useRef<RoundHistory[]>(history);
  useEffect(() => {
    historyRef.current = history;
  }, [history]);

  // Giây cuối cùng đã phát tiếng "tích" — chặn phát lặp mỗi nhịp của đồng hồ 250ms.
  const lastTickSecondRef = useRef<number>(-1);

  // Web Audio Synthesizer
  const audioCtxRef = useRef<AudioContext | null>(null);
  const getAudioCtx = () => {
    if (!audioCtxRef.current) {
      const AudioCtx =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext })
          .webkitAudioContext;
      if (AudioCtx) audioCtxRef.current = new AudioCtx();
    }
    if (audioCtxRef.current && audioCtxRef.current.state === "suspended") {
      audioCtxRef.current.resume();
    }
    return audioCtxRef.current;
  };

  // Background music controller (Asian Mood Lounge BGM)
  const bgmAudioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    const audio = new Audio("/games/xocdia/assets_hd/bgm_xocdia.mp3");
    audio.loop = true;
    audio.volume = 0.18; // Âm lượng nền nhẹ nhàng, êm dịu (18%)
    bgmAudioRef.current = audio;

    const tryPlay = () => {
      if (soundEnabled && audio.paused) {
        audio.play().catch(() => {});
      }
    };

    tryPlay();

    const handleFirstInteraction = () => {
      tryPlay();
      window.removeEventListener("pointerdown", handleFirstInteraction);
      window.removeEventListener("keydown", handleFirstInteraction);
    };

    window.addEventListener("pointerdown", handleFirstInteraction);
    window.addEventListener("keydown", handleFirstInteraction);

    return () => {
      audio.pause();
      audio.currentTime = 0;
      window.removeEventListener("pointerdown", handleFirstInteraction);
      window.removeEventListener("keydown", handleFirstInteraction);
    };
  }, []);

  // Synchronize BGM playback with soundEnabled toggle
  useEffect(() => {
    const audio = bgmAudioRef.current;
    if (!audio) return;
    if (soundEnabled) {
      audio.play().catch(() => {});
    } else {
      audio.pause();
    }
  }, [soundEnabled]);

  const playSound = (type: "chip" | "shake" | "bell" | "win" | "tick" | "chat" | "bot") => {
    if (!soundEnabled) return;
    try {
      const ctx = getAudioCtx();
      if (!ctx) return;
      const now = ctx.currentTime;

      if (type === "chip") {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = "triangle";
        osc.frequency.setValueAtTime(1600, now);
        osc.frequency.exponentialRampToValueAtTime(700, now + 0.05);
        gain.gain.setValueAtTime(0.28, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.05);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + 0.05);
      } else if (type === "shake") {
        for (let i = 0; i < 5; i++) {
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          osc.type = "sine";
          osc.frequency.setValueAtTime(800 + i * 150, now + i * 0.07);
          gain.gain.setValueAtTime(0.15, now + i * 0.07);
          gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.07 + 0.05);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(now + i * 0.07);
          osc.stop(now + i * 0.07 + 0.05);
        }
      } else if (type === "bell") {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = "sine";
        osc.frequency.setValueAtTime(880, now);
        gain.gain.setValueAtTime(0.25, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.6);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + 0.6);
      } else if (type === "win") {
        [523.25, 659.25, 783.99, 1046.5].forEach((freq, i) => {
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          osc.type = "triangle";
          osc.frequency.setValueAtTime(freq, now + i * 0.09);
          gain.gain.setValueAtTime(0.3, now + i * 0.09);
          gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.09 + 0.35);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(now + i * 0.09);
          osc.stop(now + i * 0.09 + 0.35);
        });
      } else if (type === "tick") {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = "sine";
        osc.frequency.setValueAtTime(1200, now);
        gain.gain.setValueAtTime(0.1, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.03);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + 0.03);
      } else if (type === "chat") {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = "sine";
        osc.frequency.setValueAtTime(580, now);
        osc.frequency.exponentialRampToValueAtTime(820, now + 0.06);
        gain.gain.setValueAtTime(0.15, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.06);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + 0.06);
      } else if (type === "bot") {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = "sawtooth";
        osc.frequency.setValueAtTime(320, now);
        osc.frequency.setValueAtTime(640, now + 0.09);
        gain.gain.setValueAtTime(0.2, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.22);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + 0.22);
      }
    } catch {
      // ignore
    }
  };

  // Synchronized refs for fresh values inside event handlers and timers
  const balanceRef = useRef(balance);
  const usdBalanceRef = useRef(usdBalance);
  const exchangeRateRef = useRef(exchangeRate);

  useEffect(() => {
    balanceRef.current = balance;
  }, [balance]);

  useEffect(() => {
    usdBalanceRef.current = usdBalance;
  }, [usdBalance]);

  useEffect(() => {
    exchangeRateRef.current = exchangeRate;
  }, [exchangeRate]);

  // Real-time balance synchronization:
  // 1. WebSocket / STOMP broadcast (/user/queue/wallet via NotificationContext)
  // 2. Window focus trigger (immediate refresh when tabbing back to game)
  // 3. 3.5s periodic smart polling (guarantees instant detection of admin credit)
  useEffect(() => {
    const handleWalletBalanceUpdated = (e: Event) => {
      // detail là CẢ gói WalletBalancePayload ({ balance, serverTime }), không chỉ chuỗi
      // số dư — cần `serverTime` để bỏ qua gói tới trễ.
      const payload = (e as CustomEvent<{ balance?: string; serverTime?: string }>).detail;
      const newUsd = parseFloat(String(payload?.balance ?? ""));
      if (!Number.isFinite(newUsd) || newUsd < 0) return;

      const oldUsd = usdBalanceRef.current;
      const oldVnd = balanceRef.current;
      const rate = exchangeRateRef.current;
      const newVnd = rate > 0 ? Math.round(newUsd * rate) : null;
      // Chưa biết số dư cũ (vừa mở bàn) thì chỉ ÁP, không thông báo "vừa nạp": diff so
      // với 0 sẽ báo có tiền chảy vào trong khi thực ra chỉ là lần đọc đầu tiên.
      const diffVnd = newVnd !== null && oldVnd !== null ? newVnd - oldVnd : 0;
      const diffUsd = oldUsd !== null ? newUsd - oldUsd : 0;

      applyUsdBalance(newUsd, payload?.serverTime);
      if (oldUsd === null) return;

      if (diffVnd > 0) {
        playSound("bell");
        showToast(
          `💰 NẠP TIỀN THÀNH CÔNG: +${diffVnd.toLocaleString()} đ (≈ $${diffUsd.toFixed(2)} USD)`
        );
        speakDealer(
          `Tài khoản đại gia vừa được nạp thêm +${diffVnd.toLocaleString()} đ! Cùng rinh lộc lớn thôi anh ơi! 💎✨`,
          5200
        );
      } else if (diffVnd < 0) {
        showToast(
          `⚡ Số dư cập nhật: ${(newVnd ?? 0).toLocaleString()} đ (≈ $${newUsd.toFixed(2)} USD)`
        );
      }
    };

    window.addEventListener("wallet_balance_updated", handleWalletBalanceUpdated);

    const handleFocus = () => {
      fetchBackendData();
    };
    window.addEventListener("focus", handleFocus);

    const pollTimer = setInterval(async () => {
      try {
        const wData = await walletMe();
        const newUsd = parseFloat(wData?.balance ?? "");
        if (!Number.isFinite(newUsd) || newUsd < 0) return;

        const oldUsd = usdBalanceRef.current;
        if (oldUsd !== null && Math.abs(newUsd - oldUsd) <= 0.001) return;

        const oldVnd = balanceRef.current;
        const rate = exchangeRateRef.current;
        const newVnd = rate > 0 ? Math.round(newUsd * rate) : null;
        const diffVnd = newVnd !== null && oldVnd !== null ? newVnd - oldVnd : 0;
        const diffUsd = oldUsd !== null ? newUsd - oldUsd : 0;

        // Không có `serverTime` trên đường HTTP, nhưng giá trị này VỪA được đọc nên là
        // mốc mới nhất — ghi mốc theo đồng hồ server (ước lượng) để một gói WebSocket tới
        // trễ không kéo số dư lùi về con số cũ.
        applyUsdBalance(newUsd, new Date(serverNowMs()).toISOString());
        if (oldUsd === null || diffVnd <= 0) return;

        playSound("bell");
        showToast(
          `💰 NẠP TIỀN THÀNH CÔNG: +${diffVnd.toLocaleString()} đ (≈ $${diffUsd.toFixed(2)} USD)`
        );
        speakDealer(
          `Tài khoản đại gia vừa được nạp thêm +${diffVnd.toLocaleString()} đ! Cùng rinh lộc lớn thôi anh ơi! 💎✨`,
          5200
        );
      } catch {
        // silent
      }
    }, 3500);

    return () => {
      window.removeEventListener("wallet_balance_updated", handleWalletBalanceUpdated);
      window.removeEventListener("focus", handleFocus);
      clearInterval(pollTimer);
    };
  }, []);

  // Jackpot ticker & live ambient fluctuation
  useEffect(() => {
    const interval = setInterval(() => {
      setJackpot((j) => j + Math.floor(Math.random() * 180 + 35));
      if (phase === "BETTING_OPEN") {
        setServerBets((prev) => ({
          even: +(prev.even + Math.random() * 0.25).toFixed(2),
          odd: +(prev.odd + Math.random() * 0.25).toFixed(2),
          fourRed: +(prev.fourRed + Math.random() * 0.05).toFixed(2),
          fourWhite: +(prev.fourWhite + Math.random() * 0.04).toFixed(2),
          threeWhite: +(prev.threeWhite + Math.random() * 0.06).toFixed(2),
          threeRed: +(prev.threeRed + Math.random() * 0.07).toFixed(2),
        }));
      }
    }, 1200);
    return () => clearInterval(interval);
  }, [phase]);


  // Ambient bets from other players around the table during BETTING_OPEN
  useEffect(() => {
    if (phase !== "BETTING_OPEN") return;
    const interval = setInterval(() => {
      // Only active seated bots that are NOT scheduled to leave can place bets
      const eligibleBots = activeBots.filter((b) => !pendingLeaveBotIds.includes(b.id));
      if (eligibleBots.length === 0) return;

      if (Math.random() > 0.4) {
        const randomPlayer = eligibleBots[Math.floor(Math.random() * eligibleBots.length)];
        const targetZoneKeys: Array<keyof BetState> = [
          "XOC_DIA_EVEN",
          "XOC_DIA_ODD",
          "XOC_DIA_FOUR_RED",
          "XOC_DIA_FOUR_WHITE",
          "XOC_DIA_THREE_WHITE",
          "XOC_DIA_THREE_RED",
        ];
        const chosenZone: keyof BetState =
          Math.random() < 0.74
            ? Math.random() < 0.5
              ? "XOC_DIA_EVEN"
              : "XOC_DIA_ODD"
            : targetZoneKeys[Math.floor(Math.random() * targetZoneKeys.length)];

        const bounds = BET_ZONE_BOUNDS[chosenZone];
        const randomChip = CHIP_LIST[Math.floor(Math.random() * 4)]; // 5k, 10k, 20k, 50k
        const pFromX = randomPlayer.left + 24;
        const pFromY = randomPlayer.top + 24;

        // Calibrated position strictly within the safe felt area
        const tX = Math.round(bounds.minX + Math.random() * (bounds.maxX - bounds.minX));
        const tY = Math.round(bounds.minY + Math.random() * (bounds.maxY - bounds.minY));
        const id = nextChipId.current++;

        setFlyingChips((prev) => [
          ...prev,
          {
            id,
            zone: chosenZone,
            val: randomChip.val,
            img: randomChip.img,
            fromX: pFromX,
            fromY: pFromY,
            toX: tX,
            toY: tY,
          },
        ]);

        setTimeout(() => {
          setFlyingChips((prev) => prev.filter((c) => c.id !== id));
          const landedChip: TableChip = {
            id,
            playerId: randomPlayer.id,
            zone: chosenZone,
            val: randomChip.val,
            img: randomChip.img,
            x: tX,
            y: tY,
            rotation: (Math.random() - 0.5) * 26,
          };
          setTableChips((prev) => {
            const next = [...prev, landedChip];
            tableChipsRef.current = next;
            return next;
          });

          // Occasional ambient dealer commentary on other players (data-driven real player bets)
          if (Math.random() < 0.22 && !speechTimerRef.current) {
            const doorNames: Record<keyof BetState, string> = {
              XOC_DIA_EVEN: "cửa CHẴN",
              XOC_DIA_ODD: "cửa LẺ",
              XOC_DIA_FOUR_RED: "Tứ Đỏ (1:16)",
              XOC_DIA_FOUR_WHITE: "Tứ Trắng (1:16)",
              XOC_DIA_THREE_RED: "3 Đỏ 1 Trắng",
              XOC_DIA_THREE_WHITE: "3 Trắng 1 Đỏ",
            };
            const quotes = [
              `Đại gia ${randomPlayer.name} vừa bắt ${randomChip.label} ${doorNames[chosenZone]}! 🔥`,
              `${randomPlayer.name} theo cầu ${doorNames[chosenZone]} rồi kìa các anh ơi! ✨`,
              `Bàn đang xôm lắm! ${randomPlayer.name} vừa vào ${doorNames[chosenZone]}~ 🎲`,
            ];
            speakDealer(quotes[Math.floor(Math.random() * quotes.length)], 3200);
          }
        }, 420);
      }
    }, 2000);
    return () => clearInterval(interval);
  }, [phase]);

  // Center coordinate mapping for each player avatar
  const getPlayerCoords = (playerId: string): { x: number; y: number } => {
    if (playerId === "user") {
      // Tôi (VIP) avatar center: left 125, top 380, width 52, height 52
      return { x: 151, y: 406 };
    }
    const p = PLAYERS.find((pl) => pl.id === playerId);
    if (p) {
      // Player avatar center: left + 24, top + 24
      return { x: p.left + 24, y: p.top + 24 };
    }
    return { x: 151, y: 406 };
  };

  // Winning payout animation: coins fly from winning door into winning players!
  const triggerPayoutAnimations = (redCount: number, isEven: boolean, totalWin: number = 0) => {
    const isWinningZone = (zone: keyof BetState) => {
      if (zone === "XOC_DIA_EVEN") return isEven;
      if (zone === "XOC_DIA_ODD") return !isEven;
      if (zone === "XOC_DIA_FOUR_RED") return redCount === 4;
      if (zone === "XOC_DIA_FOUR_WHITE") return redCount === 0;
      if (zone === "XOC_DIA_THREE_WHITE") return redCount === 1;
      if (zone === "XOC_DIA_THREE_RED") return redCount === 3;
      return false;
    };

    const currentChips = tableChipsRef.current;
    const currentBets = betsRef.current;

    const winners: string[] = [];
    const returns: ReturnChip[] = [];
    let animId = nextChipId.current + 8000;

    const winningChips = currentChips.filter((tc) => isWinningZone(tc.zone));
    const losingChips = currentChips.filter((tc) => !isWinningZone(tc.zone));

    // 1. Winning chips fly back into the avatar of whoever placed them
    winningChips.forEach((wc, i) => {
      if (!winners.includes(wc.playerId)) winners.push(wc.playerId);
      const target = getPlayerCoords(wc.playerId);

      // Return original chip
      returns.push({
        id: animId++,
        playerId: wc.playerId,
        fromX: wc.x,
        fromY: wc.y,
        toX: target.x,
        toY: target.y,
        img: wc.img,
        delayMs: (i % 6) * 40,
      });

      // Bonus payout profit coins streaming into the player
      const bonusCount = wc.playerId === "user" ? 3 : 2;
      for (let b = 0; b < bonusCount; b++) {
        returns.push({
          id: animId++,
          playerId: wc.playerId,
          fromX: wc.x + (Math.random() - 0.5) * 20,
          fromY: wc.y + (Math.random() - 0.5) * 16,
          toX: target.x,
          toY: target.y,
          img: wc.img,
          delayMs: 100 + b * 60 + (i % 5) * 30,
        });
      }
    });

    // 2. If user bet on a winning door and won, make sure coins stream into Tôi (VIP)
    const userBetWon = (Object.keys(currentBets) as Array<keyof BetState>).some(
      (z) => currentBets[z] > 0 && isWinningZone(z)
    );

    if (totalWin > 0 || userBetWon) {
      if (!winners.includes("user")) winners.push("user");
      const userTarget = getPlayerCoords("user");
      const winZone = (Object.keys(currentBets) as Array<keyof BetState>).find(
        (z) => isWinningZone(z) && currentBets[z] > 0
      );
      const origin =
        winZone === "XOC_DIA_EVEN"
          ? { x: 305, y: 165 }
          : winZone === "XOC_DIA_ODD"
          ? { x: 715, y: 165 }
          : winZone === "XOC_DIA_FOUR_RED"
          ? { x: 285, y: 312 }
          : winZone === "XOC_DIA_FOUR_WHITE"
          ? { x: 412, y: 312 }
          : winZone === "XOC_DIA_THREE_WHITE"
          ? { x: 612, y: 312 }
          : winZone === "XOC_DIA_THREE_RED"
          ? { x: 740, y: 312 }
          : { x: 512, y: 220 };

      const streamCount = 14;
      const houseOrigin = { x: 512, y: 220 };
      for (let s = 0; s < streamCount; s++) {
        // Xen ke: chip tu cua thang + chip tu giua song -> nhin ro tien tu song bay vao user
        const useHouse = s % 3 === 2;
        const base = useHouse ? houseOrigin : origin;
        returns.push({
          id: animId++,
          playerId: "user",
          fromX: base.x + (Math.random() - 0.5) * 55,
          fromY: base.y + (Math.random() - 0.5) * 36,
          toX: userTarget.x,
          toY: userTarget.y,
          img: CHIP_LIST[Math.min(s, CHIP_LIST.length - 1)].img,
          delayMs: 80 + s * 55,
          size: 44,
          hero: true,
          durationMs: 900,
        });
      }
      // Bubble +tien bay cung dot chip cuoi + burst khi cham avatar (dong bo cong tien 1450ms)
      //
      // Chỉ nổ bong bóng khi ĐÃ có số tiền thật: gọi từ bước mở bát (chưa chốt sổ) thì
      // `totalWin` bằng 0 và bong bóng "+0 VNĐ" vừa vô nghĩa vừa làm người chơi tưởng
      // không được trả thưởng. Số tiền thật nổ ở `applySettlement`.
      //
      // Phải là `if` bọc lấy bong bóng, KHÔNG được `return` sớm. `return` ở đây thoát khỏi
      // cả hàm, bỏ luôn đoạn dọn bàn bên dưới (`setReturnChips` / `setTableChips([])` /
      // `setWinningPlayerIds`). Mà điều kiện vào khối này là `userBetWon`, nên nó rơi ĐÚNG
      // ván người chơi thắng: màn chip thắng bay vào người chơi không bao giờ phát và chip
      // trên bàn không được xoá — hỏng ở chính ván người chơi quan tâm nhất.
      if (Math.round(totalWin) > 0) {
        const heroDelay = 80 + (streamCount - 1) * 55 + 900;
        setTimeout(() => {
          const bid = Date.now() + Math.random();
          setPayoutBursts((prev) => [
            ...prev.slice(-4),
            { id: bid, x: userTarget.x, y: userTarget.y, amount: Math.round(totalWin) },
          ]);
          playSound("win");
          setTimeout(() => {
            setPayoutBursts((prev) => prev.filter((b) => b.id !== bid));
          }, 1400);
        }, Math.max(900, heroDelay - 350));
      }
    }

    // 3. Losing chips: Dealer sweeps towards center throne at (512, 175)
    losingChips.forEach((lc, i) => {
      returns.push({
        id: animId++,
        playerId: "dealer",
        fromX: lc.x,
        fromY: lc.y,
        toX: 512 + (Math.random() - 0.5) * 36,
        toY: 175 + (Math.random() - 0.5) * 24,
        img: lc.img,
        delayMs: (i % 6) * 35,
      });
    });

    // Clear table resting chips and trigger flight
    setTableChips([]);
    tableChipsRef.current = [];
    setReturnChips(returns);
    setWinningPlayerIds(winners);
  };

  // =====================================================================
  // VÒNG ĐỜI VÁN BÁM SERVER
  // ---------------------------------------------------------------------
  // Server là nguồn sự thật DUY NHẤT cho: pha hiện tại, số ván, kết quả mở bát và tiền
  // thắng. Client chỉ còn lo hình ảnh và âm thanh.
  //
  // Trước đây chỗ này là một `setInterval` tự chạy ván 28 giây (15+2+4+5+2) trong khi
  // server cấu hình 63 giây/ván (`rwg.game.round.*` = 45+2+8+3+5). Hai đồng hồ lệch nhau
  // làm `betSeqRef` bị reset GIỮA ván server, sinh khoá trùng
  // `BET:{roundId}:{userId}:{seq}`; `BetService.placeBet` thấy khoá đã có thì trả về cược
  // CŨ kèm mã thành công, còn cược mới bị nuốt im lặng — không trừ tiền, không báo lỗi.
  // =====================================================================

  /** Lời thoại dealer khi server chuyển pha (giữ nguyên giọng cũ). */
  const narratePhase = (next: Phase) => {
    if (next === "BETTING_CLOSED") {
      playSound("bell");
      speakDealer("Hết giờ đặt cược! Bát đĩa sẵn sàng, chúc cả bàn may mắn nha! 🎲");
    } else if (next === "SPINNING") {
      playSound("shake");
      speakDealer("Xóc đều tay nè... Leng keng tài lộc về tay ai đây? ✨");
    }
  };

  /** Dọn bàn khi server mở VÁN MỚI. Chỉ được gọi lúc `roundId` đổi. */
  const onServerRoundChanged = () => {
    // Bot xin rời chỉ được rút sau khi ván trước đã trả thưởng xong.
    const toKick = pendingLeaveBotIdsRef.current;
    const targetCount = targetBotCountRef.current;
    const targetIds = PLAYERS.slice(
      0,
      Math.max(1, Math.min(targetCount, PLAYERS.length))
    ).map((p) => p.id);
    setSeatedBotIds((prev) => {
      const filtered = prev.filter((id) => !toKick.includes(id) && targetIds.includes(id));
      const updated = PLAYERS.filter((p) => filtered.includes(p.id)).map((p) => p.id);
      seatedBotIdsRef.current = updated;
      return updated;
    });
    setPendingLeaveBotIds([]);
    pendingLeaveBotIdsRef.current = [];

    const resetBets: BetState = {
      XOC_DIA_EVEN: 0,
      XOC_DIA_ODD: 0,
      XOC_DIA_FOUR_RED: 0,
      XOC_DIA_FOUR_WHITE: 0,
      XOC_DIA_THREE_RED: 0,
      XOC_DIA_THREE_WHITE: 0,
    };
    setBets(resetBets);
    betsRef.current = resetBets;
    setTableChips([]);
    tableChipsRef.current = [];
    setReturnChips([]);
    setPayoutBursts([]);
    setWinningPlayerIds([]);
    setLastWinAmount(null);

    playSound("bell");

    // Bình luận cầu bệt / câu chào. Đọc từ `historyRef` chứ không phải biến `history`
    // chụp trong closure — closure này sống lâu hơn một lần render.
    setTimeout(() => {
      const hList = historyRef.current;
      if (hList.length === 0) return;
      const lastOutcome = hList[hList.length - 1].isEven;
      let streak = 1;
      for (let i = hList.length - 2; i >= 0; i--) {
        if (hList[i].isEven === lastOutcome) streak++;
        else break;
      }
      if (streak >= 3) {
        speakDealer(`Cầu đang bệt ${lastOutcome ? "CHẴN" : "LẺ"} ${streak} ván liên tiếp rồi đó! Bám cầu hay bẻ cầu đây các anh ơi? 🔥`);
      } else {
        const welcomes = [
          "Bàn mới mở cược rồi nè các VIP, hôm nay tay em son lắm, mời anh lên thuyền! ✨",
          "Ván mới mở rồi ạ! Chúc VIP Tôi và các đại gia ván này rực rỡ nha~ ❤️",
          "Vào tiền thôi các anh ơi! Anh thích Chẵn hay Lẻ để em chiều nào? 😉",
          "May mắn đang chờ đón, các anh tự tin vào tiền rinh thưởng khủng nhé! 💰",
        ];
        speakDealer(welcomes[Math.floor(Math.random() * welcomes.length)]);
      }
    }, 300);
  };

  /**
   * Mở bát bằng kết quả THẬT của server.
   *
   * `revealedCoins` đến thẳng từ `round.xocDiaCoins` — hợp đồng đã ghi rõ ở `playerApi.ts`
   * là màn chơi PHẢI dùng field này, không được tự random. `redCount` cũng lấy từ server
   * nên hình ảnh và con số không bao giờ lệch nhau.
   */
  const revealServerResult = (round: GameRound, revealedCoins: number[], redCount: number) => {
    const resultIsEven = redCount % 2 === 0;
    setCoins(revealedCoins);

    // Xúc xắc chỉ là hiệu ứng của hũ jackpot, không mang kết quả ván.
    const dd = [1, 2, 3, 4].map(() => Math.floor(Math.random() * 6) + 1);
    if (dd.every((v) => v === dd[0])) dd[3] = (dd[3] % 6) + 1;
    setJackpotDice(dd);

    const winDoorText = resultIsEven ? "CHẴN" : "LẺ";
    // Tiền thắng THẬT về sau qua `game_result`; ở bước mở bát chỉ nói về KẾT QUẢ BÀN.
    const winningSim = tableChipsRef.current.find((tc) => {
      if (tc.playerId === "user") return false;
      if (tc.zone === "XOC_DIA_EVEN" && resultIsEven) return true;
      if (tc.zone === "XOC_DIA_ODD" && !resultIsEven) return true;
      if (tc.zone === "XOC_DIA_FOUR_RED" && redCount === 4) return true;
      if (tc.zone === "XOC_DIA_FOUR_WHITE" && redCount === 0) return true;
      if (tc.zone === "XOC_DIA_THREE_WHITE" && redCount === 1) return true;
      if (tc.zone === "XOC_DIA_THREE_RED" && redCount === 3) return true;
      return false;
    });
    if (winningSim) {
      const pName = PLAYERS.find((p) => p.id === winningSim.playerId)?.name || "đại gia";
      speakDealer(`Mở bát: ${winDoorText} (${redCount} Đỏ)! Chúc mừng đại gia ${pName} trúng lớn! VIP Tôi ván sau gỡ lại nha anh! ❤️`, 4500);
    } else {
      speakDealer(`Mở bát: ${winDoorText} (${redCount} Đỏ)! Chúc mừng các anh em đã vào đúng cửa nha! 🎉`, 4200);
    }

    // Bảng soi cầu dùng kết quả và SỐ VÁN THẬT, không phải số đếm của đồng hồ local.
    setHistory((h) => [...h.slice(-79), { seq: round.roundSeq, redCount, isEven: resultIsEven }]);

    // Chip bay về người thắng + dealer gom chip thua. Số tiền thật chưa có ở bước này
    // (server chốt sổ sau), nên chỉ chạy phần hình ảnh; bong bóng "+tiền" nổ ở
    // `applySettlement` bằng số thật.
    setTimeout(() => {
      triggerPayoutAnimations(redCount, resultIsEven, 0);
    }, 800);
  };

  /** Bong bóng "+tiền" nổ tại avatar người chơi, dùng số tiền THẬT từ server. */
  const burstUserWin = (amount: number) => {
    if (amount <= 0) return;
    const target = getPlayerCoords("user");
    const bid = Date.now() + Math.random();
    setPayoutBursts((prev) => [...prev.slice(-4), { id: bid, x: target.x, y: target.y, amount }]);
    setTimeout(() => {
      setPayoutBursts((prev) => prev.filter((b) => b.id !== bid));
    }, 1400);
  };

  /**
   * Chốt sổ một ván bằng SỐ THẬT đọc từ `GET /games/me/bets?roundId=`.
   *
   * Đường WebSocket chỉ dùng để biết "đã tới lúc" và để cập nhật số dư ngay; mọi con số
   * hiện lên bảng đều lấy từ REST. Nhờ vậy mất một gói WS (reconnect, mạng chập) không
   * làm mất tiền thắng trên màn hình.
   */
  const applySettlement = async (roundId: string) => {
    if (!roundId || settledRoundIdsRef.current.has(roundId)) return;
    settledRoundIdsRef.current.add(roundId);

    const settledBets = await myBets(roundId).catch(() => null);
    if (!settledBets) {
      // Đọc hụt: mở khoá để nhịp sau thử lại, không chốt sổ bằng số đoán.
      settledRoundIdsRef.current.delete(roundId);
      return;
    }

    // `payout` của server là stake-INCLUSIVE (M2): thắng thì nhận lại cả tiền gốc. Số dư
    // tăng thêm thật sự = payout − tổng đã đặt, nên "tiền thắng" hiện trên màn hình phải
    // trừ tiền gốc; không trừ thì báo thừa đúng bằng số tiền người chơi vừa bỏ ra.
    const rate = exchangeRateRef.current;
    const netUsd = settledBets.reduce(
      (sum, b) => sum + (Number(b.payout || 0) - Number(b.stake || 0)),
      0
    );
    const netVnd = rate > 0 ? Math.round(netUsd * rate) : 0;

    // Ghép từng phiếu trong sổ phiên với bản ghi cược tương ứng để hiện đúng lời/lỗ của
    // từng lần bấm, thay vì chia đều tổng cho mọi dòng cùng ván.
    setSessionBetLogs((prev) => {
      const byType = new Map<string, PlayerBet[]>();
      for (const b of settledBets) {
        byType.set(b.betType, [...(byType.get(b.betType) ?? []), b]);
      }
      const cursor = new Map<string, number>();
      return prev.map((log) => {
        if (log.roundId !== roundId) return log;
        const group = byType.get(log.betType) ?? [];
        const i = cursor.get(log.betType) ?? 0;
        if (i >= group.length) return log;
        cursor.set(log.betType, i + 1);
        const bet = group[i];
        const perBetNet =
          rate > 0 ? Math.round((Number(bet.payout || 0) - Number(bet.stake || 0)) * rate) : 0;
        return { ...log, won: perBetNet > 0, payout: perBetNet };
      });
    });

    if (netVnd > 0) {
      setSessionTotalWon((prev) => prev + netVnd);
      setLastWinAmount(netVnd);
      playSound("win");
      burstUserWin(netVnd);
      // Dealer chúc mừng bằng SỐ THẬT vừa nhận, không phải con số nhân tay từ odds cứng.
      speakDealer(
        `Oaaa! Chúc mừng VIP Tôi húp trọn +${Math.round(netVnd / 1000).toLocaleString()}K! Tay anh son quá, tối bao em nha~ 😉🥂`,
        5000
      );
    } else {
      setLastWinAmount(null);
    }
  };

  /** Đồng bộ trạng thái từ một gói vòng của server. */
  const applyServerRound = (round: GameRound) => {
    // Đồng hồ: mọi mốc tính theo giờ SERVER. Máy người dùng lệch giờ là chuyện thường,
    // so với giờ máy thì đồng hồ đếm ngược sai ngay từ nhịp đầu.
    const srvNow = Date.parse(round.serverTime);
    if (!Number.isNaN(srvNow)) serverClockOffsetRef.current = srvNow - Date.now();
    phaseEndsAtRef.current = round.phaseEndsAt ? Date.parse(round.phaseEndsAt) : null;
    if (!Number.isNaN(srvNow) && phaseEndsAtRef.current !== null) {
      setTimeLeft(Math.max(0, Math.ceil((phaseEndsAtRef.current - srvNow) / 1000)));
    }

    const nextPhase = round.phase as Phase;
    const prevPhase = phaseRef.current;
    // Lần đồng bộ ĐẦU TIÊN không phải chuyển pha: người chơi vừa mở bàn và server đang ở
    // giữa ván. Nói "hết giờ đặt cược" lúc đó là sai thời điểm.
    const isFirstSync = firstSyncRef.current;

    // VÁN MỚI: đây là chỗ DUY NHẤT được reset `betSeqRef`. Khoá chống trùng của ví gắn
    // với `roundId` của SERVER, nên reset theo ván của đồng hồ local là mở đường cho hai
    // lần đặt khác nhau dùng chung một khoá — và lần thứ hai bị server trả về cược cũ.
    if (round.roundId !== serverRoundIdRef.current) {
      const previousRoundId = serverRoundIdRef.current;
      serverRoundIdRef.current = round.roundId;
      betSeqRef.current = 0;
      revealedRoundIdRef.current = null;
      onServerRoundChanged();

      // Khôi phục số thứ tự đã dùng trong ván này. Sau khi tải lại trang, `betSeqRef` về 0
      // trong khi server đã có cược của chính người này ở ĐÚNG ván đó; đặt tiếp bằng seq 0
      // sẽ trùng khoá và bị trả về cược CŨ — đúng lỗi đang sửa, chỉ khác đường vào.
      void myBets(round.roundId)
        .then((mine) => {
          if (mine.length > betSeqRef.current) betSeqRef.current = mine.length;
        })
        .catch(() => {});

      // Lưới an toàn cho đường REST: nếu gói WebSocket của ván vừa rồi thất lạc (mất mạng,
      // đang kết nối lại), chốt sổ nốt ở đây. `settledRoundIdsRef` bảo đảm không cộng hai lần.
      if (previousRoundId) void applySettlement(previousRoundId);
    }
    firstSyncRef.current = false;

    phaseRef.current = nextPhase;
    setPhase(nextPhase);
    setRoundSeq(round.roundSeq);
    if (!isFirstSync && prevPhase !== nextPhase) narratePhase(nextPhase);

    // Mở bát: kết quả đã được server ghi vào vòng TRƯỚC khi chuyển sang pha RESULT.
    // `parseXocDiaCoins` trả null khi dữ liệu hỏng -> giữ bát úp và thử lại ở nhịp poll
    // sau, KHÔNG tự random ra một kết quả khác.
    if (
      (nextPhase === "RESULT" || nextPhase === "SETTLE") &&
      revealedRoundIdRef.current !== round.roundId
    ) {
      const parsed = parseXocDiaCoins(round.xocDiaCoins);
      if (parsed) {
        revealedRoundIdRef.current = round.roundId;
        revealServerResult(
          round,
          parsed,
          round.xocDiaRedCount ?? parsed.reduce((a, b) => a + b, 0)
        );
      }
    }
  };

  // Ref giữ bản mới nhất của các hàm đồng bộ, để vòng poll 1 giây không phải huỷ và tạo
  // lại `setInterval` sau mỗi lần component render.
  const applyServerRoundRef = useRef<(round: GameRound) => void>(() => {});
  const applySettlementRef = useRef<(roundId: string) => void>(() => {});
  useEffect(() => {
    applyServerRoundRef.current = applyServerRound;
    applySettlementRef.current = (roundId) => void applySettlement(roundId);
  });

  // Poll vòng hiện tại. 404 `ROUND_NOT_FOUND` giữa hai ván là BÌNH THƯỜNG (server đã đóng
  // vòng cũ, chưa mở vòng mới) — giữ nguyên trạng thái cuối, không reset bàn.
  useEffect(() => {
    if (!xocTableId) return;
    let cancelled = false;
    const tick = async () => {
      try {
        const round = await currentRound(xocTableId);
        if (!cancelled && round) applyServerRoundRef.current(round);
      } catch (err) {
        if (err instanceof ApiError && err.code !== "ROUND_NOT_FOUND") {
          console.warn("Đồng bộ vòng Xóc Đĩa thất bại:", err);
        }
      }
    };
    void tick();
    const timer = setInterval(tick, 1000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [xocTableId]);

  // Đồng hồ đếm ngược: nội suy từ mốc kết thúc pha do server trả về, giữa hai nhịp poll.
  // Tự trừ 1 giây mỗi nhịp như trước thì đồng hồ trôi dần và lệch hẳn khỏi server.
  useEffect(() => {
    const timer = setInterval(() => {
      const endsAt = phaseEndsAtRef.current;
      if (endsAt === null) return;
      const left = Math.max(0, Math.ceil((endsAt - serverNowMs()) / 1000));
      setTimeLeft(left);

      // Nhắc "còn 5 giây" đúng MỘT lần cho mỗi giây, không phải mỗi nhịp 250ms.
      if (phaseRef.current === "BETTING_OPEN" && left > 0 && left <= 5) {
        if (lastTickSecondRef.current !== left) {
          lastTickSecondRef.current = left;
          playSound("tick");
          if (left === 5) {
            speakDealer("Còn 5 giây chốt cược thôi ạ! Các anh nhanh tay nha~ ⏳");
          }
        }
      } else {
        lastTickSecondRef.current = -1;
      }
    }, 250);
    return () => clearInterval(timer);
    // `soundEnabled` nằm trong deps vì `playSound` đọc nó: để `[]` thì effect giữ mãi
    // closure của lượt render đầu, và tắt âm thanh rồi mà tiếng "tick" vẫn kêu.
    // Vòng lặp chỉ đọc ref nên dựng lại khi bật/tắt tiếng là vô hại.
  }, [soundEnabled]);

  // Tiền thắng + số dư thật do server đẩy về sau khi chốt sổ.
  useEffect(() => {
    const handleGameResult = (e: Event) => {
      const detail = (e as CustomEvent<{
        tableId?: string;
        roundId?: string;
        balanceAfter?: string;
        serverTime?: string;
      }>).detail;
      if (!detail || detail.tableId !== xocTableIdRef.current) return;

      // Số dư sau khi trả thưởng: áp ngay để người chơi thấy tiền về.
      const after = parseFloat(String(detail.balanceAfter ?? ""));
      if (Number.isFinite(after)) applyUsdBalance(after, detail.serverTime);

      // Mọi con số hiện lên bảng lấy từ REST, kể cả khi gói này tới muộn hoặc không tới.
      if (detail.roundId) applySettlementRef.current(detail.roundId);
    };
    window.addEventListener("game_result", handleGameResult);
    return () => window.removeEventListener("game_result", handleGameResult);
  }, []);

  // Place bet at EXACT mouse click location without missing/drift ("k dc trật")
  const placeBet = async (
    zone: keyof BetState,
    e?: React.MouseEvent,
    fallbackX: number = 512,
    fallbackY: number = 250
  ) => {
    if (phase !== "BETTING_OPEN") {
      const lockMsg =
        phase === "BETTING_CLOSED"
          ? "Đã hết giờ đặt cược! Chờ xóc đĩa xong ván mới vào tiền nhé! ⛔"
          : phase === "SPINNING"
          ? "Đang xóc đĩa... Ngừng nhận cược, chờ mở bát nhé! 🎲"
          : "Đang mở bát trả thưởng... Ván mới mở rồi vào tiền nhé! 💰";
      showToast(lockMsg);
      playSound("tick");
      return;
    }
    // Tien that: goi placeBet server TRUOC khi hien chip. Server tu tru vi (M1)
    // va tra balanceAfter — dung lam nguon su that duy nhat, khong tru local.
    if (placingRef.current) return;
    const ensureTableAndRound = async (): Promise<string | null> => {
      let tableId = xocTableIdRef.current;
      if (!tableId) {
        try {
          const tables = await gameTables();
          const xoc = tables.find((t) => t.gameType === "XOC_DIA" && t.status === "ACTIVE") || tables.find((t) => t.gameType === "XOC_DIA");
          if (xoc) {
            tableId = xoc.id;
            xocTableIdRef.current = xoc.id;
            setXocTableId(xoc.id);
          }
        } catch {
          return null;
        }
      }
      if (!tableId) return null;
      if (!serverRoundIdRef.current) {
        try {
          const round = await currentRound(tableId);
          if (round?.roundId) serverRoundIdRef.current = round.roundId;
        } catch {
          // Ke giua 2 vong: de serverRoundId null, van cho dat de server tu resolve round OPEN.
        }
      }
      return tableId;
    };
    const rate = exchangeRateRef.current || 25000;
    const stakeUsdStr = (() => {
      const raw = selectedChip / rate;
      // Giu toi da 4 decimals, cat zero thua (backend nhan string thap phan).
      return raw.toFixed(4).replace(/0+$/, "").replace(/\.$/, "");
    })();
    const seq = betSeqRef.current;
    // Vòng server thực nhận cược này; gán trong `try`, đọc lại sau khi đặt xong.
    let placedRoundId: string | null = null;
    placingRef.current = true;
    try {
      const tableId = await ensureTableAndRound();
      if (!tableId) {
        showToast("Chưa kết nối được bàn Xóc Đĩa! Vui lòng thử lại.");
        placingRef.current = false;
        return;
      }
      const res = await apiPlaceBet(tableId, { betType: zone, selection: "", stake: stakeUsdStr, seq });
      if (res?.roundId) serverRoundIdRef.current = res.roundId;
      placedRoundId = res?.roundId ?? null;
      betSeqRef.current = seq + 1;
      if (res?.balanceAfter) applyUsdBalance(parseFloat(res.balanceAfter));
    } catch (err) {
      // seq chua tieu thu khi server tu choi (chua debit) -> giu nguyen de thu lai.
      placingRef.current = false;
      if (err instanceof ApiError) {
        if (err.code === "ROUND_BETTING_CLOSED" || err.code === "ROUND_NOT_FOUND") {
          showToast("Đã hết giờ đặt cược! Chờ ván mới vào tiền nhé! ⛔");
        } else if (err.code === "INSUFFICIENT_BALANCE") {
          showToast(`Số dư không đủ! (Còn ${balanceRef.current?.toLocaleString() ?? "—"} đ ≈ $${usdBalanceRef.current?.toFixed(2) ?? "—"} USD)`);
          try {
            const w = await walletMe();
            if (w?.balance) applyUsdBalance(parseFloat(w.balance));
          } catch {
            // silent
          }
        } else {
          showToast("Đặt cược thất bại! Vui lòng thử lại.");
        }
      } else {
        showToast("Mất kết nối! Kiểm tra mạng rồi đặt lại nhé.");
      }
      playSound("tick");
      return;
    }
    placingRef.current = false;
    playSound("chip");

    const bounds = BET_ZONE_BOUNDS[zone];
    let targetX = bounds.defaultX;
    let targetY = bounds.defaultY;

    if (e && containerRef.current) {
      const rect = containerRef.current.getBoundingClientRect();
      const clickX = Math.round((e.clientX - rect.left) / scale);
      const clickY = Math.round((e.clientY - rect.top) / scale);
      // Clamp strictly within the safe felt area of the door so chips never spill off-card or cover bottom number bars
      targetX = Math.max(bounds.minX, Math.min(bounds.maxX, clickX));
      targetY = Math.max(bounds.minY, Math.min(bounds.maxY, clickY));
    } else {
      // Natural stack scatter if clicked programmatically or fallback
      targetX = bounds.defaultX + Math.round((Math.random() - 0.5) * 16);
      targetY = bounds.defaultY + Math.round((Math.random() - 0.5) * 12);
    }

    // User avatar (Tôi - VIP): left 125, top 380 -> center (151, 406)
    const fromX = 151;
    const fromY = 406;

    const chipConfig = CHIP_LIST.find((c) => c.val === selectedChip) || CHIP_LIST[1];
    const id = nextChipId.current++;

    const newChip: FlyingChip = {
      id,
      zone,
      fromX,
      fromY,
      toX: targetX,
      toY: targetY,
      val: selectedChip,
      img: chipConfig.img,
    };

    setFlyingChips((prev) => [...prev, newChip]);

    const rotation = Math.round((Math.random() - 0.5) * 24);

    // Flight takes 420ms: upon landing, add to tableChips so it remains on the door until round ends!
    setTimeout(() => {
      setFlyingChips((prev) => prev.filter((c) => c.id !== id));
      const landedChip: TableChip = {
        id,
        playerId: "user",
        zone,
        val: selectedChip,
        img: chipConfig.img,
        x: targetX,
        y: targetY,
        rotation,
      };
      setTableChips((prev) => {
        const next = [...prev, landedChip];
        tableChipsRef.current = next;
        return next;
      });
      playSound("chip");
    }, 420);

    // Da tru vi that qua balanceAfter o tren — khong tru local them.

    // Intelligent NPC Dealer reaction to VIP Tôi's real bet
    const chipLabel = chipConfig.label;
    // Tỷ lệ đọc từ server: người này có thể được đặt tỷ lệ riêng, nói "1 ăn 16" cứng
    // là dealer đọc sai số tiền người chơi thật sự nhận được. Đọc qua `oddsTextRef`
    // (không gọi `oddsText`) — xem chú thích ở khai báo ref.
    const oddsTextForBet = (zone: keyof BetState): string => oddsTextRef.current[zone] ?? "";
    const doorTitles: Record<keyof BetState, string> = {
      XOC_DIA_EVEN: "cửa CHẴN",
      XOC_DIA_ODD: "cửa LẺ",
      XOC_DIA_FOUR_RED: `TỨ ĐỎ (1 ăn ${oddsTextForBet("XOC_DIA_FOUR_RED")})`,
      XOC_DIA_FOUR_WHITE: `TỨ TRẮNG (1 ăn ${oddsTextForBet("XOC_DIA_FOUR_WHITE")})`,
      XOC_DIA_THREE_RED: "3 ĐỎ 1 TRẮNG",
      XOC_DIA_THREE_WHITE: "3 TRẮNG 1 ĐỎ",
    };

    const nextBetAmount = bets[zone] + selectedChip;
    if (selectedChip >= 500000) {
      speakDealer(
        `Đại gia Tôi vào mạnh ${chipLabel} ${doorTitles[zone]}! Đẳng cấp là đây, chúc anh đại thắng nha! 💎🔥`,
        4000
      );
    } else if (zone === "XOC_DIA_FOUR_RED" || zone === "XOC_DIA_FOUR_WHITE") {
      speakDealer(
        `VIP Tôi bắt ${doorTitles[zone]} kìa! Nổ một phát 1 ăn ${oddsTextForBet(zone)} là rực rỡ cả sảnh luôn anh ơi! 🚀✨`,
        4200
      );
    } else if (nextBetAmount >= 100000 && bets[zone] > 0) {
      speakDealer(
        `VIP Tôi quyết tâm bồi thêm ${chipLabel} vào ${doorTitles[zone]}! Em cổ vũ anh hết mình nè~ ❤️`,
        3800
      );
    } else {
      const betQuotes = [
        `VIP Tôi vào ${chipLabel} ${doorTitles[zone]}! Chúc anh may mắn rinh thưởng khủng nha~ ✨`,
        `Tay anh Tôi vào ${doorTitles[zone]} đẹp quá! Vía son nổ to nhé anh ơi! 🍀`,
        `Cửa ${doorTitles[zone]} có thêm ${chipLabel} của VIP Tôi rồi nè! Em mong anh ăn trọn ván này~ ❤️`,
      ];
      speakDealer(betQuotes[Math.floor(Math.random() * betQuotes.length)], 3500);
    }

    setBets((prev) => {
      const next = {
        ...prev,
        [zone]: prev[zone] + selectedChip,
      };
      betsRef.current = next;
      return next;
    });

    const now = new Date();
    const timeStr = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}:${String(now.getSeconds()).padStart(2, "0")}`;
    setSessionBetLogs((prev) => [
      {
        id: Date.now() + Math.random(),
        roundSeq,
        // Vòng SERVER mà server đã ghi cược này vào (server trả về), không phải vòng
        // đang hiện trên đồng hồ local — hai thứ này từng lệch nhau.
        roundId: placedRoundId ?? serverRoundIdRef.current,
        betType: zone,
        zoneName: doorTitles[zone],
        stake: selectedChip,
        payout: 0,
        won: false,
        time: timeStr,
      },
      ...prev.slice(0, 49),
    ]);
  };

  // Chip Carousel Scrolling & Dragging
  const chipScrollRef = useRef<HTMLDivElement>(null);
  const isDraggingChipRef = useRef(false);
  const startXRef = useRef(0);
  const scrollLeftRef = useRef(0);

  const scrollChips = (direction: "left" | "right") => {
    if (!chipScrollRef.current) return;
    chipScrollRef.current.scrollBy({
      left: direction === "left" ? -140 : 140,
      behavior: "smooth",
    });
  };

  const handleChipMouseDown = (e: React.MouseEvent) => {
    if (!chipScrollRef.current) return;
    isDraggingChipRef.current = true;
    startXRef.current = e.pageX - chipScrollRef.current.offsetLeft;
    scrollLeftRef.current = chipScrollRef.current.scrollLeft;
  };

  const handleChipMouseMove = (e: React.MouseEvent) => {
    if (!isDraggingChipRef.current || !chipScrollRef.current) return;
    e.preventDefault();
    const x = e.pageX - chipScrollRef.current.offsetLeft;
    const walk = (x - startXRef.current) * 1.5;
    chipScrollRef.current.scrollLeft = scrollLeftRef.current - walk;
  };

  const handleChipMouseUpOrLeave = () => {
    isDraggingChipRef.current = false;
  };

  const formatBetBadge = (amount: number) => {
    if (amount >= 1000000) {
      const m = amount / 1000000;
      return `$${m % 1 === 0 ? m : m.toFixed(1)}M`;
    }
    return `$${(amount / 1000).toLocaleString()}K`;
  };

  const redCount = coins.reduce((a, b) => a + b, 0);
  const isEven = redCount % 2 === 0;
  const isBettingLocked = phase !== "BETTING_OPEN";
  // Số dư chỉ hiện "—" khi CHƯA đọc được từ ví. Trước đây hai chỗ này khởi tạo cứng
  // 5.030.000 đ nên tài khoản trắng vẫn trông như có tiền.
  const balanceLabel = balance === null ? "—" : `${balance.toLocaleString()} đ`;
  const balanceUsdLabel =
    balance === null || exchangeRate <= 0
      ? "—"
      : `$${(balance / exchangeRate).toLocaleString(undefined, {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })}`;

  return (
    <div className="relative w-full h-full min-h-dvh bg-[#050302] flex items-center justify-center select-none overflow-hidden font-sans">
      {/* ========================================================= */}
      {/* 1024x507 VIRTUAL STAGE (100% REBUILT WITH AI GENERATED ASSETS) */}
      {/* ========================================================= */}
      <div
        ref={containerRef}
        className="relative overflow-hidden shadow-[0_0_100px_rgba(0,0,0,1)]"
        style={{
          width: 1024,
          height: 507,
          transform: `scale(${scale})`,
          transformOrigin: "center center",
        }}
      >
        {/* ========================================================= */}
        {/* LAYER 0: 3D CASINO TABLE MASTER (AI 4K GENERATED BASE) */}
        {/* Completely clean table surface with golden Trong Dong & Dragons */}
        {/* ========================================================= */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src="/games/xocdia/assets_hd/casino_table_master.jpg"
          alt="Casino Table"
          className="absolute inset-0 w-full h-full object-cover pointer-events-none z-0"
        />

        {/* Ambient table center glow */}
        <div className="absolute left-1/2 top-[48%] -translate-x-1/2 -translate-y-1/2 w-[480px] h-[220px] rounded-full bg-amber-500/10 blur-[50px] pointer-events-none z-1" />

        {/* ========================================================= */}
        {/* LAYER 1: DEALER ON ROYAL GOLDEN THRONE (TOP CENTER) */}
        {/* ========================================================= */}
        <div
          onClick={handleDealerClick}
          className="absolute left-[437px] top-[0px] w-[150px] h-[150px] z-15 cursor-pointer pointer-events-auto group drop-shadow-[0_12px_24px_rgba(0,0,0,0.9)] transition-transform duration-200 hover:scale-105 active:scale-95"
          title="Nhấn để trò chuyện với Dealer Vy Vy"
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/games/xocdia/assets_hd/dealer_throne_alpha.png"
            alt="Dealer"
            className="w-full h-full object-contain"
          />
          {/* Interactive touch hint */}
          <div className="absolute bottom-5 left-1/2 -translate-x-1/2 opacity-0 group-hover:opacity-100 transition-opacity bg-black/85 text-amber-300 text-[9px] px-2 py-0.5 rounded-full border border-amber-500/40 pointer-events-none whitespace-nowrap shadow-md">
            Trò chuyện 💕
          </div>
        </div>

        {/* Dealer Speech Bubble - Positioned directly to the right of her head */}
        {dealerSpeech && (
          <div
            key={dealerSpeech.id}
            onClick={handleDealerClick}
            className="absolute left-[542px] top-[8px] w-[218px] z-40 animate-in fade-in zoom-in-95 duration-200 pointer-events-auto cursor-pointer select-none"
            title="Nhấn để nghe câu khác"
          >
            <div className="relative bg-gradient-to-br from-[#1c130e]/95 via-[#130d09]/95 to-[#0b0705]/95 border border-amber-500/60 shadow-[0_8px_24px_rgba(0,0,0,0.9),0_0_12px_rgba(245,158,11,0.25)] rounded-2xl rounded-tl-sm px-3 py-2 text-left backdrop-blur-md">
              {/* Tail pointing left directly to dealer head */}
              <div className="absolute -left-[7px] top-[14px] w-0 h-0 border-t-[5px] border-t-transparent border-b-[5px] border-b-transparent border-r-[7px] border-r-amber-500/70" />
              <div className="absolute -left-[5px] top-[14px] w-0 h-0 border-t-[5px] border-t-transparent border-b-[5px] border-b-transparent border-r-[7px] border-r-[#1c130e]" />

              {/* Header: Status & Dealer Name */}
              <div className="flex items-center justify-between mb-1">
                <div className="flex items-center gap-1.5">
                  <span className="relative flex h-1.5 w-1.5">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                    <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-emerald-500" />
                  </span>
                  <span className="text-[9px] font-black uppercase tracking-wider text-amber-400 font-mono">
                    DEALER VY VY
                  </span>
                </div>
                <span className="text-[8px] font-medium text-amber-400/60 uppercase">
                  Genting VIP
                </span>
              </div>

              {/* Body Text */}
              <p className="text-[11px] font-semibold text-amber-100 leading-[15px] drop-shadow-[0_1px_2px_rgba(0,0,0,0.9)]">
                {dealerSpeech.text}
              </p>
            </div>
          </div>
        )}

        {/* ========================================================= */}
        {/* LAYER 2: TOP CONTROLS & BADGES */}
        {/* ========================================================= */}
        {/* 2.1 Exit Button */}
        <Link
          href="/"
          className="absolute left-[14px] top-[14px] w-[34px] h-[34px] rounded-full z-30 flex items-center justify-center bg-black/70 border border-amber-500/60 shadow-lg text-amber-300 hover:brightness-125 active:scale-90 transition-all"
          title="Rời bàn"
        >
          <ChevronLeft className="w-5 h-5" />
        </Link>

        {/* 2.2 Leaderboard */}
        <button
          onClick={() => setShowTopWins(true)}
          className="absolute left-[54px] top-[14px] w-[34px] h-[34px] rounded-full z-30 flex items-center justify-center bg-black/70 border border-amber-500/60 shadow-lg text-amber-300 hover:brightness-125 active:scale-90 transition-all cursor-pointer"
          title="Bảng xếp hạng"
        >
          <Trophy className="w-4 h-4" />
        </button>

        {/* 2.3 Rules */}
        <button
          onClick={() => setShowRules(true)}
          className="absolute left-[94px] top-[14px] w-[34px] h-[34px] rounded-full z-30 flex items-center justify-center bg-black/70 border border-amber-500/60 shadow-lg text-amber-300 hover:brightness-125 active:scale-90 transition-all cursor-pointer"
          title="Luật chơi"
        >
          <HelpCircle className="w-4 h-4" />
        </button>

        {/* 2.4 JACKPOT Display Badge (Top Left of Table) */}
        {/* Using AI-generated Golden Crowned Jackpot Plaque with Empty Number Slot */}
        <div className="absolute left-[255px] top-[10px] w-[158px] h-[48px] z-20 select-none pointer-events-none drop-shadow-[0_4px_16px_rgba(0,0,0,0.9)]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/games/xocdia/assets_hd/jackpot_plaque.png"
            alt="Jackpot"
            className="w-full h-full object-contain"
          />
          {/* Dynamic Value Slot: {jackpot} */}
          <div className="absolute left-[8%] bottom-[4px] w-[84%] h-[20px] flex items-center justify-center">
            <span className="text-[12px] font-mono font-black text-[#ffea79] tracking-wider drop-shadow-[0_1px_4px_rgba(0,0,0,0.95)]">
              {jackpot.toLocaleString()}
            </span>
          </div>
        </div>

        {/* 2.5 DICE DISPLAY BOX (Top Right of Table) */}
        {/* Using AI-generated Golden Dice Box Container + 4 RubyDice Tu Quy */}
        <div className="absolute left-[615px] top-[12px] w-[148px] h-[46px] z-20 select-none pointer-events-none drop-shadow-[0_4px_16px_rgba(0,0,0,0.9)]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/games/xocdia/assets_hd/dice_box.png"
            alt="Dice Box"
            className="w-full h-full object-contain"
          />
          <div className="absolute inset-0 flex items-center justify-center gap-[5px] pb-[3px]">
            {jackpotDice.map((d, i) => (
              <RubyDice key={`${roundSeq}-${i}`} value={d} size={22} isRolling={phase === "SPINNING"} />
            ))}
          </div>
        </div>

        {/* 2.6 Sound Toggle */}
        <button
          onClick={() => setSoundEnabled(!soundEnabled)}
          className="absolute right-[54px] top-[14px] w-[34px] h-[34px] rounded-full z-30 flex items-center justify-center bg-black/70 border border-amber-500/60 shadow-lg text-amber-300 hover:brightness-125 active:scale-90 transition-all cursor-pointer"
          title="Âm thanh"
        >
          {soundEnabled ? (
            <Volume2 className="w-4 h-4 text-amber-300 drop-shadow" />
          ) : (
            <VolumeX className="w-4 h-4 text-red-400 drop-shadow" />
          )}
        </button>

        {/* 2.7 Chat Button */}
        <button
          onClick={() => {
            setShowChat((prev) => {
              if (!prev) setUnreadChatCount(0);
              return !prev;
            });
          }}
          className="absolute right-[14px] top-[14px] w-[34px] h-[34px] rounded-full z-30 flex items-center justify-center bg-black/70 border border-amber-500/60 shadow-lg text-amber-300 hover:brightness-125 active:scale-90 transition-all cursor-pointer"
          title="Phòng Chat Bàn VIP"
        >
          <MessageSquare className="w-4 h-4" />
          {unreadChatCount > 0 && !showChat && (
            <span className="absolute -top-1.5 -right-1.5 min-w-[17px] h-[17px] px-1 bg-gradient-to-r from-red-600 to-rose-500 rounded-full border border-amber-300 text-[9px] font-black text-white flex items-center justify-center animate-pulse shadow-[0_0_8px_rgba(239,68,68,0.9)] pointer-events-none">
              {unreadChatCount > 9 ? "9+" : unreadChatCount}
            </span>
          )}
        </button>

        {/* ========================================================= */}
        {/* LAYER 3: 3D BETTING CARDS - CHẴN & LẺ (AI GENERATED ASSETS) */}
        {/* ========================================================= */}
        {/* 3.1 CARD CHẴN (GOLDEN 3D CARD) */}
        <div
          onClick={(e) => placeBet("XOC_DIA_EVEN", e, 310, 149)}
          title={isBettingLocked ? "Đang xóc — ngừng nhận cược" : `Đặt cửa Chẵn${oddsHint("XOC_DIA_EVEN")}`}
          className={`absolute left-[195px] top-[95px] w-[225px] h-[142px] transition-all duration-200 select-none z-20 ${
            phase === "RESULT" && isEven
              ? "scale-105 filter drop-shadow-[0_0_30px_rgba(250,204,21,1)] animate-pulse cursor-default"
              : isBettingLocked
              ? "cursor-not-allowed opacity-80 saturate-50 brightness-90 drop-shadow-[0_10px_20px_rgba(0,0,0,0.85)]"
              : "cursor-pointer hover:scale-[1.02] active:scale-[0.98] drop-shadow-[0_10px_20px_rgba(0,0,0,0.85)]"
          }`}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/games/xocdia/assets_hd/card_chan_clean.png"
            alt="Chẵn"
            className="w-full h-full object-contain pointer-events-none"
          />

          {/* Dynamic Value Slot: {serverBets.even}M */}
          <div className="absolute left-[12%] bottom-[16%] w-[76%] h-[26px] flex items-center justify-center pointer-events-none">
            <span className="text-[13px] font-mono font-black text-[#ffea79] tracking-wider drop-shadow-[0_1px_3px_rgba(0,0,0,0.9)]">
              {serverBets.even}M
            </span>
          </div>

          {/* User Bet Indicator Chip */}
          {bets.XOC_DIA_EVEN > 0 && (
            <div className="absolute top-2 right-4 px-2 py-0.5 rounded-full bg-gradient-to-r from-amber-400 to-yellow-500 text-black font-black text-[10px] shadow-lg border border-white animate-bounce pointer-events-none">
              {formatBetBadge(bets.XOC_DIA_EVEN)}
            </div>
          )}
        </div>

        {/* 3.2 CARD LẺ (CRIMSON/MAGENTA 3D CARD) */}
        <div
          onClick={(e) => placeBet("XOC_DIA_ODD", e, 710, 149)}
          title={isBettingLocked ? "Đang xóc — ngừng nhận cược" : `Đặt cửa Lẻ${oddsHint("XOC_DIA_ODD")}`}
          className={`absolute left-[605px] top-[95px] w-[225px] h-[142px] transition-all duration-200 select-none z-20 ${
            phase === "RESULT" && !isEven
              ? "scale-105 filter drop-shadow-[0_0_30px_rgba(244,63,94,1)] animate-pulse cursor-default"
              : isBettingLocked
              ? "cursor-not-allowed opacity-80 saturate-50 brightness-90 drop-shadow-[0_10px_20px_rgba(0,0,0,0.85)]"
              : "cursor-pointer hover:scale-[1.02] active:scale-[0.98] drop-shadow-[0_10px_20px_rgba(0,0,0,0.85)]"
          }`}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/games/xocdia/assets_hd/card_le_clean.png"
            alt="Lẻ"
            className="w-full h-full object-contain pointer-events-none"
          />

          {/* Dynamic Value Slot: {serverBets.odd}M */}
          <div className="absolute left-[12%] bottom-[16%] w-[76%] h-[26px] flex items-center justify-center pointer-events-none">
            <span className="text-[13px] font-mono font-black text-[#ffb4d6] tracking-wider drop-shadow-[0_1px_3px_rgba(0,0,0,0.9)]">
              {serverBets.odd}M
            </span>
          </div>

          {/* User Bet Indicator Chip */}
          {bets.XOC_DIA_ODD > 0 && (
            <div className="absolute top-2 right-4 px-2 py-0.5 rounded-full bg-gradient-to-r from-rose-500 to-pink-500 text-white font-black text-[10px] shadow-lg border border-white animate-bounce pointer-events-none">
              {formatBetBadge(bets.XOC_DIA_ODD)}
            </div>
          )}
        </div>

        {/* ========================================================= */}
        {/* LAYER 4: CENTER BÁT ĐĨA 3D (PIXI.JS WEBGL ENGINE) */}
        {/* ========================================================= */}
        <div className="absolute left-[404px] top-[85px] w-[215px] h-[195px] z-25 flex flex-col items-center justify-center pointer-events-auto">
          <div className="relative w-full h-full flex items-center justify-center">
            <XocDiaCanvas
              phase={phase}
              coins={coins}
              width={215}
              height={195}
            />
          </div>
        </div>

        {/* ========================================================= */}
        {/* LAYER 5: 4 VỊ BETTING STRIP (AI GENERATED ASSET) */}
        {/* ========================================================= */}
        <div className="absolute left-[227px] top-[242px] w-[570px] h-[142px] z-20 select-none drop-shadow-[0_12px_25px_rgba(0,0,0,0.9)]">
          {/* Background Sprite */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/games/xocdia/assets_hd/four_vi_clean.png"
            alt="4 Vị"
            className="w-full h-full object-contain pointer-events-none"
          />

          {/* 5.1 Hitbox & Slot 1: 4 ĐỎ (1:16) */}
          <div
            onClick={(e) => placeBet("XOC_DIA_FOUR_RED", e, 285, 312)}
            title={isBettingLocked ? "Đang xóc — ngừng nhận cược" : `Đặt cửa 4 Đỏ${oddsHint("XOC_DIA_FOUR_RED")}`}
            style={{
              borderRadius: "34px 14px 13px 16px / 24px 14px 13px 16px",
            }}
            className={`absolute left-[40px] top-[17px] w-[117px] h-[97px] transition-all duration-300 ${
              (phase === "RESULT" || phase === "SETTLE") && redCount === 4
                ? "border-[2.5px] border-rose-400 shadow-[0_0_25px_rgba(244,63,94,1),inset_0_0_16px_rgba(244,63,94,0.4)] bg-rose-500/25 animate-pulse scale-[1.02] cursor-default"
                : isBettingLocked
                ? "border border-transparent cursor-not-allowed opacity-70 saturate-50"
                : "border border-transparent hover:border-amber-400/40 hover:bg-white/5 active:scale-[0.98] cursor-pointer"
            }`}
          >
            {/* Dynamic Value Slot: {serverBets.fourRed}M */}
            <div className="absolute left-0 top-[56px] w-full h-[24px] flex items-center justify-center pointer-events-none">
              <span className="text-[11px] font-mono font-bold text-[#ffea79] drop-shadow">
                {serverBets.fourRed}M
              </span>
            </div>
            {bets.XOC_DIA_FOUR_RED > 0 && (
              <div className="absolute top-1 right-2 px-1.5 py-0.2 rounded-full bg-red-600 text-white font-black text-[8px] shadow border border-white">
                {formatBetBadge(bets.XOC_DIA_FOUR_RED)}
              </div>
            )}
          </div>

          {/* 5.2 Hitbox & Slot 2: 4 TRẮNG (1:16) */}
          <div
            onClick={(e) => placeBet("XOC_DIA_FOUR_WHITE", e, 412, 312)}
            title={isBettingLocked ? "Đang xóc — ngừng nhận cược" : `Đặt cửa 4 Trắng${oddsHint("XOC_DIA_FOUR_WHITE")}`}
            style={{ borderRadius: "14px" }}
            className={`absolute left-[160px] top-[17px] w-[121px] h-[97px] transition-all duration-300 ${
              (phase === "RESULT" || phase === "SETTLE") && redCount === 0
                ? "border-[2.5px] border-white shadow-[0_0_25px_#ffffff,inset_0_0_18px_rgba(255,255,255,0.45)] bg-white/20 animate-pulse scale-[1.02] cursor-default"
                : isBettingLocked
                ? "border border-transparent cursor-not-allowed opacity-70 saturate-50"
                : "border border-transparent hover:border-amber-400/40 hover:bg-white/5 active:scale-[0.98] cursor-pointer"
            }`}
          >
            <div className="absolute left-0 top-[56px] w-full h-[24px] flex items-center justify-center pointer-events-none">
              <span className="text-[11px] font-mono font-bold text-[#ffea79] drop-shadow">
                {serverBets.fourWhite}M
              </span>
            </div>
            {bets.XOC_DIA_FOUR_WHITE > 0 && (
              <div className="absolute top-1.5 right-1.5 px-1.5 py-0.2 rounded-full bg-slate-200 text-black font-black text-[8px] shadow border border-white">
                {formatBetBadge(bets.XOC_DIA_FOUR_WHITE)}
              </div>
            )}
          </div>

          {/* 5.3 Hitbox & Slot 3: 3 TRẮNG 1 ĐỎ (1:4) */}
          <div
            onClick={(e) => placeBet("XOC_DIA_THREE_WHITE", e, 612, 312)}
            title={isBettingLocked ? "Đang xóc — ngừng nhận cược" : `Đặt cửa 3 Trắng 1 Đỏ${oddsHint("XOC_DIA_THREE_WHITE")}`}
            style={{ borderRadius: "14px" }}
            className={`absolute left-[286px] top-[17px] w-[121px] h-[97px] transition-all duration-300 ${
              (phase === "RESULT" || phase === "SETTLE") && redCount === 1
                ? "border-[2.5px] border-white shadow-[0_0_25px_#ffffff,inset_0_0_18px_rgba(255,255,255,0.45)] bg-white/20 animate-pulse scale-[1.02] cursor-default"
                : isBettingLocked
                ? "border border-transparent cursor-not-allowed opacity-70 saturate-50"
                : "border border-transparent hover:border-amber-400/40 hover:bg-white/5 active:scale-[0.98] cursor-pointer"
            }`}
          >
            <div className="absolute left-0 top-[56px] w-full h-[24px] flex items-center justify-center pointer-events-none">
              <span className="text-[11px] font-mono font-bold text-[#ffea79] drop-shadow">
                {serverBets.threeWhite}M
              </span>
            </div>
            {bets.XOC_DIA_THREE_WHITE > 0 && (
              <div className="absolute top-1.5 right-1.5 px-1.5 py-0.2 rounded-full bg-slate-200 text-black font-black text-[8px] shadow border border-white">
                {formatBetBadge(bets.XOC_DIA_THREE_WHITE)}
              </div>
            )}
          </div>

          {/* 5.4 Hitbox & Slot 4: 3 ĐỎ 1 TRẮNG (1:4) */}
          <div
            onClick={(e) => placeBet("XOC_DIA_THREE_RED", e, 740, 312)}
            title={isBettingLocked ? "Đang xóc — ngừng nhận cược" : `Đặt cửa 3 Đỏ 1 Trắng${oddsHint("XOC_DIA_THREE_RED")}`}
            style={{
              borderRadius: "14px 34px 16px 13px / 14px 24px 16px 13px",
            }}
            className={`absolute left-[412px] top-[17px] w-[118px] h-[97px] transition-all duration-300 ${
              (phase === "RESULT" || phase === "SETTLE") && redCount === 3
                ? "border-[2.5px] border-rose-400 shadow-[0_0_25px_rgba(244,63,94,1),inset_0_0_16px_rgba(244,63,94,0.4)] bg-rose-500/25 animate-pulse scale-[1.02] cursor-default"
                : isBettingLocked
                ? "border border-transparent cursor-not-allowed opacity-70 saturate-50"
                : "border border-transparent hover:border-amber-400/40 hover:bg-white/5 active:scale-[0.98] cursor-pointer"
            }`}
          >
            <div className="absolute left-0 top-[56px] w-full h-[24px] flex items-center justify-center pointer-events-none">
              <span className="text-[11px] font-mono font-bold text-[#ffea79] drop-shadow">
                {serverBets.threeRed}M
              </span>
            </div>
            {bets.XOC_DIA_THREE_RED > 0 && (
              <div className="absolute top-1 right-2 px-1.5 py-0.2 rounded-full bg-red-600 text-white font-black text-[8px] shadow border border-white">
                {formatBetBadge(bets.XOC_DIA_THREE_RED)}
              </div>
            )}
          </div>
        </div>

        {/* ========================================================= */}
        {/* LAYER 6: PLAYERS AROUND THE TABLE (VIP GOLD FRAMED AVATARS) */}
        {/* ========================================================= */}
        {activeBots.map((p) => {
          const isWinner = winningPlayerIds.includes(p.id);
          const isPendingDeparture = pendingLeaveBotIds.includes(p.id);
          return (
            <div
              key={p.id}
              style={{ left: p.left, top: p.top }}
              className={`absolute z-25 flex flex-col items-center select-none pointer-events-none drop-shadow-[0_6px_14px_rgba(0,0,0,0.9)] transition-all duration-500 animate-in fade-in zoom-in-90 ${
                isWinner ? "scale-110 -translate-y-1" : ""
              } ${isPendingDeparture ? "opacity-75 scale-95" : ""}`}
            >
              <div
                className={`relative w-[48px] h-[48px] rounded-full overflow-hidden transition-all duration-300 ${
                  isWinner
                    ? "ring-4 ring-yellow-300 shadow-[0_0_22px_#fde047] animate-pulse"
                    : "ring-1 ring-amber-500/40"
                }`}
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={p.avatar}
                  alt={p.name}
                  className="w-full h-full object-cover"
                />
                {isWinner && (
                  <div className="absolute inset-0 bg-yellow-400/25 animate-ping pointer-events-none" />
                )}
              </div>
              <div
                className={`mt-0.5 px-2 py-0.2 rounded-md border shadow text-center leading-tight max-w-[70px] transition-all duration-300 ${
                  isWinner
                    ? "bg-amber-950/95 border-yellow-400 text-yellow-300 shadow-[0_0_10px_#fde047]"
                    : "bg-black/85 border-amber-500/50"
                }`}
              >
                <div className="text-[8px] font-bold text-slate-200 truncate">
                  {p.name}
                </div>
                <div
                  className={`text-[8px] font-mono font-bold ${
                    isWinner ? "text-yellow-300" : "text-[#ffea79]"
                  }`}
                >
                  {p.balance} đ
                </div>
              </div>
              {isWinner && (
                <div className="absolute -top-3.5 left-1/2 -translate-x-1/2 px-1.5 py-0.2 rounded-full bg-yellow-400 text-black font-black text-[8px] shadow border border-white animate-bounce whitespace-nowrap z-30">
                  WIN!
                </div>
              )}
            </div>
          );
        })}

        {/* Hero User Avatar (Bottom Left) */}
        {(() => {
          const isUserWinner = winningPlayerIds.includes("user");
          return (
            <div
              style={{ left: 125, top: 380 }}
              className={`absolute z-25 flex flex-col items-center select-none pointer-events-none drop-shadow-[0_8px_18px_rgba(0,0,0,0.95)] transition-all duration-300 ${
                isUserWinner ? "scale-110 -translate-y-1" : ""
              }`}
            >
              <div
                className={`relative w-[52px] h-[52px] rounded-full overflow-hidden transition-all duration-300 ${
                  isUserWinner
                    ? "ring-4 ring-yellow-300 shadow-[0_0_25px_#fde047,0_0_50px_rgba(250,204,21,0.9)] animate-pulse"
                    : "ring-2 ring-amber-400"
                }`}
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src="/games/xocdia/assets_hd/avatar_1.png"
                  alt="Me"
                  className="w-full h-full object-cover"
                />
                {isUserWinner && (
                  <div className="absolute inset-0 bg-yellow-400/30 animate-ping pointer-events-none" />
                )}
              </div>
              <div
                className={`mt-0.5 px-2 py-0.5 rounded-md border shadow text-center leading-tight transition-all duration-300 ${
                  isUserWinner
                    ? "bg-amber-950/95 border-yellow-400 shadow-[0_0_15px_#fde047]"
                    : "bg-black/90 border-amber-400"
                }`}
              >
                <div className="text-[9px] font-black text-amber-300 truncate max-w-[84px]">
                  {realUsername} (VIP)
                </div>
                <div
                  className={`text-[10px] font-mono font-black ${
                    isUserWinner ? "text-yellow-300 animate-bounce" : "text-emerald-400"
                  }`}
                  title={`≈ ${balanceUsdLabel} USD (${exchangeRateFormatted})`}
                >
                  {balanceLabel}
                </div>
              </div>
              {isUserWinner && lastWinAmount && (
                <div className="absolute -top-5 left-1/2 -translate-x-1/2 px-2 py-0.5 rounded-full bg-gradient-to-r from-amber-400 to-yellow-300 text-black font-black text-[10px] shadow-xl border border-white animate-bounce whitespace-nowrap z-30">
                  +{(lastWinAmount / 1000).toLocaleString()}K
                </div>
              )}
            </div>
          );
        })()}

        {/* ========================================================= */}
        {/* STATUS & COUNTDOWN CAPSULE (PLACED DIRECTLY ABOVE CHIP TRAY) */}
        {/* ========================================================= */}
        <div className="absolute left-1/2 -translate-x-1/2 bottom-[86px] z-30 w-[148px] h-[36px] flex items-center justify-center pointer-events-none drop-shadow-[0_6px_16px_rgba(0,0,0,0.95)]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/games/xocdia/assets_hd/countdown_pill.png"
            alt="Countdown"
            className="w-full h-full object-contain"
          />
          {/* Dynamic Value in empty slot */}
          <div className="absolute left-[44%] top-[2px] bottom-[2px] right-[15%] flex items-center justify-center">
            {phase === "BETTING_OPEN" ? (
              <span className="text-[12px] font-mono font-black text-red-400 animate-pulse drop-shadow-[0_1px_3px_rgba(0,0,0,0.9)]">
                {timeLeft}s
              </span>
            ) : phase === "BETTING_CLOSED" ? (
              <span className="text-[9px] font-black text-red-300 uppercase drop-shadow">
                KHÓA
              </span>
            ) : phase === "SPINNING" ? (
              <span className="text-[9px] font-black text-amber-300 uppercase animate-pulse drop-shadow -translate-x-1">
                XÓC...
              </span>
            ) : phase === "RESULT" ? (
              <span className="text-[9px] font-black text-emerald-300 uppercase animate-bounce drop-shadow">
                {isEven ? "CHẴN" : "LẺ"}
              </span>
            ) : (
              <span className="text-[9px] font-bold text-slate-200 uppercase drop-shadow">
                XONG
              </span>
            )}
          </div>
        </div>

        {/* ========================================================= */}
        {/* LAYER 7: CHIP SELECTOR TRAY (BOTTOM CENTER - SCROLLABLE & DRAGGABLE) */}
        {/* ========================================================= */}
        <div
          className="absolute left-[265px] bottom-[6px] w-[440px] h-[76px] rounded-full flex items-center px-2 z-30 select-none shadow-2xl"
          style={{
            background: "linear-gradient(180deg, #2b1307 0%, #150803 100%)",
            border: "2.5px solid #a16207",
            boxShadow: "0 8px 30px rgba(0,0,0,0.95), inset 0 2px 4px rgba(255,215,0,0.4)",
          }}
        >
          {/* Scroll Left Button */}
          <button
            onClick={() => scrollChips("left")}
            className="text-amber-400 hover:text-amber-200 active:scale-90 transition-all p-1.5 shrink-0 cursor-pointer z-10 hover:brightness-125"
            title="Kéo sang trái"
          >
            <ChevronLeft className="w-6 h-6 drop-shadow" />
          </button>

          {/* Draggable & Scrollable Chip Carousel */}
          <div
            ref={chipScrollRef}
            onMouseDown={handleChipMouseDown}
            onMouseMove={handleChipMouseMove}
            onMouseUp={handleChipMouseUpOrLeave}
            onMouseLeave={handleChipMouseUpOrLeave}
            onWheel={(e) => {
              if (chipScrollRef.current) {
                chipScrollRef.current.scrollLeft += e.deltaY;
              }
            }}
            className="flex-1 flex items-center gap-2.5 overflow-x-auto scroll-smooth py-2 px-1 cursor-grab active:cursor-grabbing"
            style={{
              scrollbarWidth: "none",
              msOverflowStyle: "none",
            }}
          >
            {CHIP_LIST.map((c) => {
              const isSelected = selectedChip === c.val;
              // Chưa đọc được số dư thì không chặn chọn chip — server vẫn là chốt cuối
              // và sẽ trả INSUFFICIENT_BALANCE nếu thật sự không đủ tiền.
              const isAffordable = balance === null || balance >= c.val;

              return (
                <div
                  key={c.val}
                  onClick={() => {
                    if (!isAffordable) {
                      showToast(`Số dư không đủ để chọn chip ${c.label}!`);
                      return;
                    }
                    playSound("chip");
                    setSelectedChip(c.val);
                  }}
                  className={`relative w-[50px] h-[50px] shrink-0 rounded-full select-none transition-all duration-200 flex items-center justify-center ${
                    !isAffordable
                      ? "opacity-55 cursor-not-allowed filter contrast-90"
                      : isSelected
                      ? "scale-110 -translate-y-1.5 z-20 cursor-pointer"
                      : "opacity-90 hover:opacity-100 hover:scale-105 active:scale-95 drop-shadow-[0_4px_10px_rgba(0,0,0,0.8)] cursor-pointer"
                  }`}
                  title={isAffordable ? `Chọn chip ${c.label}` : `Số dư không đủ (${c.label})`}
                >
                  {/* Radiant Spotlight & Halo when selected ("sáng đèn") */}
                  {isSelected && isAffordable && (
                    <>
                      {/* Pulsing light aura behind the chip */}
                      <div className="absolute -inset-2 rounded-full bg-gradient-to-tr from-amber-400/60 via-yellow-300/80 to-amber-200/60 blur-xs animate-pulse pointer-events-none -z-10" />
                      <div className="absolute -inset-1 rounded-full bg-yellow-300/50 blur-xs pointer-events-none -z-10" />
                      {/* Top bright highlight beacon */}
                      <div className="absolute -top-1 left-1/2 -translate-x-1/2 w-2 h-2 rounded-full bg-white shadow-[0_0_8px_#ffffff,0_0_14px_#fde047] z-30" />
                    </>
                  )}

                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={c.img}
                    alt={c.label}
                    className={`w-full h-full object-contain pointer-events-none rounded-full transition-all duration-200 ${
                      !isAffordable
                        ? "brightness-85 saturate-60"
                        : isSelected
                        ? "ring-[3px] ring-[#fde047] shadow-[0_0_20px_#fde047,0_0_35px_rgba(250,204,21,0.85)] brightness-120 contrast-105"
                        : "ring-1 ring-black/40"
                    }`}
                    draggable={false}
                  />
                </div>
              );
            })}
          </div>

          {/* Scroll Right Button */}
          <button
            onClick={() => scrollChips("right")}
            className="text-amber-400 hover:text-amber-200 active:scale-90 transition-all p-1.5 shrink-0 cursor-pointer z-10 hover:brightness-125"
            title="Kéo sang phải"
          >
            <ChevronRight className="w-6 h-6 drop-shadow" />
          </button>
        </div>

        {/* ========================================================= */}
        {/* LAYER 8: SOI CẦU / ROADMAP (BOTTOM RIGHT) */}
        {/* ========================================================= */}
        <div className="absolute right-[10px] bottom-[6px] w-[195px] h-[104px] z-25 select-none drop-shadow-[0_8px_20px_rgba(0,0,0,0.9)]">
          {/* Background Board Sprite */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/games/xocdia/assets_hd/roadmap_board.png"
            alt="Roadmap"
            className="w-full h-full object-contain pointer-events-none"
          />

          {/* Header Count: CHẴN */}
          <div className="absolute left-[33%] top-[12%] w-[12%] h-[16%] flex items-center justify-center pointer-events-none">
            <span className="text-[10px] font-mono font-black text-amber-300 drop-shadow">
              {history.filter((h) => h.isEven).length}
            </span>
          </div>

          {/* Header Count: LẺ */}
          <div className="absolute left-[78%] top-[12%] w-[12%] h-[16%] flex items-center justify-center pointer-events-none">
            <span className="text-[10px] font-mono font-black text-rose-300 drop-shadow">
              {history.filter((h) => !h.isEven).length}
            </span>
          </div>

          {/* Dynamic Beads in Recessed Tray (4 rows x 15 cols = 60 slots for rich casino trend/cầu analysis) */}
          <div className="absolute left-[8%] top-[34%] right-[8%] bottom-[16%] px-1 py-0.5 grid grid-rows-4 grid-flow-col gap-x-[2px] gap-y-[2px] items-center justify-items-center pointer-events-none">
            {Array.from({ length: 60 }).map((_, i) => {
              const sliceStart = Math.max(0, history.length - 60);
              const items = history.slice(sliceStart);
              const h = items[i];
              const isLatest = items.length > 0 && i === items.length - 1;
              return (
                <div
                  key={i}
                  className="w-[9.5px] h-[9.5px] rounded-full bg-black/50 border border-amber-950/40 flex items-center justify-center relative"
                >
                  {h && (
                    <div
                      className={`w-[8.5px] h-[8.5px] rounded-full flex items-center justify-center transition-all ${
                        h.isEven
                          ? h.redCount === 4
                            ? "bg-gradient-to-b from-red-400 to-red-700 border border-amber-300 shadow-[0_0_4px_#f59e0b]"
                            : h.redCount === 0
                            ? "bg-gradient-to-b from-white to-slate-300 border border-amber-300 shadow-[0_0_4px_#f59e0b]"
                            : "bg-gradient-to-b from-red-500 to-red-700 border border-red-300/80 shadow-[0_1px_2px_rgba(239,68,68,0.7)]"
                          : "bg-gradient-to-b from-white to-slate-200 border border-slate-400/80 shadow-[0_1px_2px_rgba(255,255,255,0.7)]"
                      } ${
                        isLatest
                          ? "ring-1.5 ring-yellow-300 shadow-[0_0_6px_#fde047] animate-pulse scale-110 z-10"
                          : ""
                      }`}
                      title={`Ván #${h.seq}: ${h.isEven ? "CHẴN" : "LẺ"} (${h.redCount} Đỏ)`}
                    >
                      <span
                        className={`w-[2.5px] h-[2.5px] rounded-full ${
                          h.isEven ? "bg-white" : "bg-red-600"
                        }`}
                      />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* ========================================================= */}
        {/* LAYER 9: TABLE RESTING CHIPS & DYNAMIC FLYING CHIPS */}
        {/* ========================================================= */}
        <style
          dangerouslySetInnerHTML={{
            __html: `
              @keyframes flyChipArc {
                0% {
                  transform: translate3d(-50%, -50%, 0) scale(0.65) rotate(0deg);
                  opacity: 0.85;
                }
                30% {
                  transform: translate3d(calc(-50% + var(--dx) * 0.3), calc(-50% + var(--dy) * 0.3 - 48px), 0) scale(1.2) rotate(110deg);
                  opacity: 1;
                }
                65% {
                  transform: translate3d(calc(-50% + var(--dx) * 0.7), calc(-50% + var(--dy) * 0.7 - 24px), 0) scale(1.05) rotate(240deg);
                  opacity: 1;
                }
                85% {
                  transform: translate3d(calc(-50% + var(--dx) * 0.9), calc(-50% + var(--dy) * 0.9 - 6px), 0) scale(0.95) rotate(320deg);
                  opacity: 1;
                }
                100% {
                  transform: translate3d(calc(-50% + var(--dx)), calc(-50% + var(--dy)), 0) scale(1) rotate(360deg);
                  opacity: 1;
                }
              }

              @keyframes returnCoinArcHero {
                0% {
                  transform: translate3d(-50%, -50%, 0) scale(0.7) rotate(0deg);
                  opacity: 0.9;
                }
                25% {
                  transform: translate3d(calc(-50% + var(--rdx) * 0.25), calc(-50% + var(--rdy) * 0.25 - 70px), 0) scale(1.45) rotate(140deg);
                  opacity: 1;
                }
                60% {
                  transform: translate3d(calc(-50% + var(--rdx) * 0.65), calc(-50% + var(--rdy) * 0.65 - 34px), 0) scale(1.25) rotate(300deg);
                  opacity: 1;
                }
                85% {
                  transform: translate3d(calc(-50% + var(--rdx) * 0.92), calc(-50% + var(--rdy) * 0.92 - 8px), 0) scale(0.9) rotate(420deg);
                  opacity: 1;
                }
                100% {
                  transform: translate3d(calc(-50% + var(--rdx)), calc(-50% + var(--rdy)), 0) scale(0.4) rotate(520deg);
                  opacity: 0;
                }
              }

              @keyframes payoutBurstPop {
                0% { transform: translate(-50%, -50%) scale(0.4); opacity: 0; }
                20% { transform: translate(-50%, -78%) scale(1.25); opacity: 1; }
                55% { transform: translate(-50%, -110%) scale(1.05); opacity: 1; }
                100% { transform: translate(-50%, -160%) scale(0.9); opacity: 0; }
              }

              @keyframes payoutRingExpand {
                0% { transform: translate(-50%, -50%) scale(0.5); opacity: 0.9; }
                100% { transform: translate(-50%, -50%) scale(2.2); opacity: 0; }
              }

              @keyframes returnCoinArc {
                0% {
                  transform: translate3d(-50%, -50%, 0) scale(1) rotate(0deg);
                  opacity: 1;
                }
                25% {
                  transform: translate3d(calc(-50% + var(--rdx) * 0.25), calc(-50% + var(--rdy) * 0.25 - 45px), 0) scale(1.28) rotate(130deg);
                  opacity: 1;
                }
                65% {
                  transform: translate3d(calc(-50% + var(--rdx) * 0.7), calc(-50% + var(--rdy) * 0.7 - 22px), 0) scale(1.1) rotate(290deg);
                  opacity: 1;
                }
                90% {
                  transform: translate3d(calc(-50% + var(--rdx) * 0.95), calc(-50% + var(--rdy) * 0.95 - 4px), 0) scale(0.85) rotate(390deg);
                  opacity: 0.95;
                }
                100% {
                  transform: translate3d(calc(-50% + var(--rdx)), calc(-50% + var(--rdy)), 0) scale(0.35) rotate(480deg);
                  opacity: 0;
                }
              }
            `,
          }}
        />

        {/* RESTING CHIPS ON BETTING DOORS (PERSIST UNTIL QUAY XONG / RESULT IS SETTLED) */}
        <div className="absolute inset-0 pointer-events-none z-25">
          {tableChips.map((tc, idx) => {
            const isWinningZone =
              phase === "RESULT" &&
              ((tc.zone === "XOC_DIA_EVEN" && isEven) ||
                (tc.zone === "XOC_DIA_ODD" && !isEven) ||
                (tc.zone === "XOC_DIA_FOUR_RED" && redCount === 4) ||
                (tc.zone === "XOC_DIA_FOUR_WHITE" && redCount === 0) ||
                (tc.zone === "XOC_DIA_THREE_WHITE" && redCount === 1) ||
                (tc.zone === "XOC_DIA_THREE_RED" && redCount === 3));

            return (
              <div
                key={tc.id}
                className={`absolute w-[32px] h-[32px] rounded-full drop-shadow-[0_4px_8px_rgba(0,0,0,0.85)] transition-all ${
                  isWinningZone
                    ? "scale-115 ring-2 ring-yellow-400 shadow-[0_0_15px_#facc15] animate-pulse"
                    : ""
                }`}
                style={{
                  left: tc.x,
                  top: tc.y,
                  transform: `translate(-50%, -50%) rotate(${tc.rotation}deg)`,
                  zIndex: 25 + (idx % 30),
                }}
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={tc.img}
                  alt="Placed Chip"
                  className="w-full h-full object-contain rounded-full"
                />
              </div>
            );
          })}

          {/* DYNAMIC FLYING CHIPS (FLYING FROM PLAYER INTO EXACT CLICK POSITION) */}
          {flyingChips.map((fc) => (
            <div
              key={fc.id}
              className="absolute w-[32px] h-[32px] z-50 pointer-events-none drop-shadow-[0_10px_20px_rgba(0,0,0,0.95)]"
              style={{
                left: fc.fromX,
                top: fc.fromY,
                ['--dx' as any]: `${fc.toX - fc.fromX}px`,
                ['--dy' as any]: `${fc.toY - fc.fromY}px`,
                animation: 'flyChipArc 0.42s cubic-bezier(0.22, 1, 0.36, 1) forwards',
              }}
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={fc.img}
                alt="Flying Chip"
                className="w-full h-full object-contain rounded-full brightness-115"
              />
            </div>
          ))}

          {/* DYNAMIC RETURN & PAYOUT COINS (COINS FLYING INTO WINNING PLAYERS) */}
          {returnChips.map((rc) => {
            const chipSize = rc.size || 32;
            const isHero = !!rc.hero;
            const duration = (rc.durationMs || 520) / 1000;
            return (
              <div
                key={rc.id}
                className={`absolute z-50 pointer-events-none ${
                  isHero
                    ? "drop-shadow-[0_10px_24px_rgba(255,215,0,0.95)]"
                    : "drop-shadow-[0_8px_18px_rgba(255,215,0,0.85)]"
                }`}
                style={{
                  width: chipSize,
                  height: chipSize,
                  left: rc.fromX,
                  top: rc.fromY,
                  ['--rdx' as any]: `${rc.toX - rc.fromX}px`,
                  ['--rdy' as any]: `${rc.toY - rc.fromY}px`,
                  animation: `${isHero ? "returnCoinArcHero" : "returnCoinArc"} ${duration}s cubic-bezier(0.22, 1, 0.36, 1) ${rc.delayMs}ms forwards`,
                }}
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={rc.img}
                  alt="Return Coin"
                  className={`w-full h-full object-contain rounded-full brightness-120 ${
                    isHero ? "ring-[3px] ring-yellow-200 shadow-[0_0_18px_#fde047]" : "ring-2 ring-yellow-300 shadow-[0_0_12px_#fde047]"
                  }`}
                />
              </div>
            );
          })}

          {/* PAYOUT BURST KHI CHIP CHAM AVATAR: vong sang + so tien noi len */}
          {payoutBursts.map((b) => (
            <div key={b.id} className="absolute z-[60] pointer-events-none" style={{ left: b.x, top: b.y }}>
              <div
                className="absolute w-[90px] h-[90px] rounded-full border-[3px] border-yellow-300"
                style={{ animation: "payoutRingExpand 0.7s ease-out forwards" }}
              />
              <div
                className="absolute -translate-x-1/2 px-3 py-1 rounded-full bg-gradient-to-r from-amber-400 to-yellow-300 text-black font-black text-[13px] shadow-xl border-2 border-white whitespace-nowrap"
                style={{ animation: "payoutBurstPop 1.4s ease-out forwards" }}
              >
                +{(b.amount / 1000).toLocaleString()}K
              </div>
            </div>
          ))}
        </div>

        {/* ========================================================= */}
        {/* LAYER 10: BIG WIN CELEBRATION (3D ASSET) */}
        {/* ========================================================= */}
        {lastWinAmount && (
          <div className="absolute inset-x-0 top-[100px] z-50 flex items-center justify-center pointer-events-none">
            <div className="relative w-[360px] h-[198px] flex items-center justify-center drop-shadow-[0_20px_45px_rgba(0,0,0,0.95)] animate-bounce">
              {/* 3D Plaque Asset */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src="/games/xocdia/assets_hd/win_popup.png"
                alt="Thắng Lớn"
                className="w-full h-full object-contain"
              />
              {/* Dynamic Win Amount in empty velvet slot */}
              <div className="absolute left-[22%] top-[45%] w-[56%] h-[20%] flex items-center justify-center">
                <span className="text-[20px] font-mono font-black text-[#ffea79] tracking-wider drop-shadow-[0_2px_8px_rgba(0,0,0,0.95)] animate-pulse">
                  +{lastWinAmount.toLocaleString()} VNĐ
                </span>
              </div>
            </div>
          </div>
        )}

        {/* ========================================================= */}
        {/* LAYER 11: MODALS (Rules & Top Wins - High-End Genting Redesign) */}
        {/* ========================================================= */}
        {showRules && (
          <div className="absolute inset-0 z-50 flex items-center justify-center p-1.5 sm:p-2.5 bg-black/85 backdrop-blur-xs animate-in fade-in duration-200">
            <div className="bg-gradient-to-b from-[#211409] via-[#140b05] to-[#0a0502] border-2 border-amber-500/70 rounded-3xl max-w-[540px] w-full shadow-[0_0_60px_rgba(0,0,0,0.95),0_0_30px_rgba(245,158,11,0.25)] text-xs text-white relative overflow-hidden flex flex-col max-h-[485px]">
              {/* Gold Top Light bar */}
              <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-amber-600 via-yellow-300 to-amber-600 shadow-[0_0_12px_rgba(245,158,11,0.8)]" />

              {/* Header */}
              <div className="flex items-center justify-between px-5 pt-3.5 pb-2.5 border-b border-amber-500/30">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-full bg-gradient-to-br from-amber-400 to-yellow-600 flex items-center justify-center shadow-lg border border-amber-300/60">
                    <HelpCircle className="w-4 h-4 text-black" />
                  </div>
                  <div>
                    <h3 className="font-black text-transparent bg-clip-text bg-gradient-to-r from-amber-200 via-yellow-300 to-amber-400 text-sm tracking-wide uppercase font-serif">
                      LUẬT CHƠI & TỶ LỆ TRẢ THƯỞNG
                    </h3>
                    <p className="text-[10px] text-amber-400/70 font-mono tracking-wider">
                      SẢNH XÓC ĐĨA CỬU LONG GENTING VIP
                    </p>
                  </div>
                </div>

                <button
                  onClick={() => setShowRules(false)}
                  className="w-7 h-7 rounded-full bg-stone-900 border border-stone-700 hover:border-red-500/60 text-slate-300 hover:text-red-400 flex items-center justify-center transition-all cursor-pointer font-bold active:scale-95"
                >
                  ✕
                </button>
              </div>

              {/* Rules Content Grid */}
              <div 
                className="p-4 space-y-2.5 overflow-y-auto max-h-[350px]"
                style={{ scrollbarWidth: 'thin', scrollbarColor: '#d97706 rgba(0,0,0,0.4)' }}
              >
                <div className="grid grid-cols-2 gap-2">
                  <div className="p-2.5 rounded-2xl bg-black/50 border border-yellow-500/40">
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-black text-yellow-400 text-xs">CỬA CHẴN</span>
                      <span className="text-[10px] font-mono font-bold px-1.5 py-0.5 rounded bg-yellow-500/20 text-yellow-300 border border-yellow-500/40">
                        1 : 1.98
                      </span>
                    </div>
                    <p className="text-[10px] text-slate-300 leading-relaxed">
                      Trúng khi ra <strong>4 Đỏ</strong>, <strong>4 Trắng</strong>, hoặc <strong>2 Đỏ 2 Trắng</strong>.
                    </p>
                  </div>

                  <div className="p-2.5 rounded-2xl bg-black/50 border border-rose-500/40">
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-black text-rose-400 text-xs">CỬA LẺ</span>
                      <span className="text-[10px] font-mono font-bold px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-300 border border-rose-500/40">
                        1 : 1.98
                      </span>
                    </div>
                    <p className="text-[10px] text-slate-300 leading-relaxed">
                      Trúng khi ra <strong>3 Đỏ 1 Trắng</strong> hoặc <strong>3 Trắng 1 Đỏ</strong>.
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div className="p-2.5 rounded-2xl bg-gradient-to-r from-red-950/60 to-black/60 border border-amber-500/50">
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-black text-amber-300 text-xs">TỨ TỬ (4 ĐỎ / 4 TRẮNG)</span>
                      <span className="text-[10px] font-mono font-bold px-1.5 py-0.5 rounded bg-amber-500/30 text-amber-200 border border-amber-400/60 animate-pulse">
                        1 : 16
                      </span>
                    </div>
                    <p className="text-[10px] text-slate-300 leading-relaxed">
                      Nổ hũ siêu khủng khi ra chính xác <strong>4 hạt cùng màu đỏ</strong> hoặc <strong>4 hạt cùng màu trắng</strong>.
                    </p>
                  </div>

                  <div className="p-2.5 rounded-2xl bg-black/50 border border-amber-500/30">
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-black text-amber-300 text-xs">3 ĐỎ / 3 TRẮNG</span>
                      <span className="text-[10px] font-mono font-bold px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300 border border-amber-500/40">
                        1 : 4
                      </span>
                    </div>
                    <p className="text-[10px] text-slate-300 leading-relaxed">
                      Trúng khi ra đúng <strong>3 Đỏ 1 Trắng</strong> hoặc <strong>3 Trắng 1 Đỏ</strong>.
                    </p>
                  </div>
                </div>

                <div className="p-3 rounded-2xl bg-black/60 border border-amber-500/20 space-y-1">
                  <span className="text-[10px] font-bold text-amber-400 uppercase tracking-wider block font-mono">
                    ● TÍNH NĂNG ĐẶC BIỆT
                  </span>
                  <p className="text-[11px] text-slate-300 leading-relaxed">
                    • <strong>Nặn Bát Tương Tác:</strong> Khi mở bát, đại gia có thể rê chuột hoặc chạm tay vuốt nặn bát từ từ để hồi hộp đón nhận kết quả.
                  </p>
                  <p className="text-[11px] text-slate-300 leading-relaxed">
                    • <strong>Xác Thực SHA-256:</strong> Kết quả mỗi vòng quay được mã hóa chuỗi SHA-256 chuẩn quốc tế, bảo mật tuyệt đối và ngẫu nhiên 100%.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {showTopWins && (
          <div className="absolute inset-0 z-50 flex items-center justify-center p-1.5 sm:p-2.5 bg-black/85 backdrop-blur-xs animate-in fade-in duration-200">
            <div className="bg-gradient-to-b from-[#211409] via-[#140b05] to-[#0a0502] border-2 border-amber-500/70 rounded-3xl max-w-[560px] w-full shadow-[0_0_60px_rgba(0,0,0,0.95),0_0_30px_rgba(245,158,11,0.25)] text-xs text-white relative overflow-hidden flex flex-col max-h-[485px]">
              {/* Gold Top Light bar */}
              <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-amber-600 via-yellow-300 to-amber-600 shadow-[0_0_12px_rgba(245,158,11,0.8)]" />

              {/* Modal Header */}
              <div className="flex items-center justify-between px-5 pt-3.5 pb-2.5 border-b border-amber-500/30">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-full bg-gradient-to-br from-amber-400 to-yellow-600 flex items-center justify-center shadow-lg border border-amber-300/60">
                    <Trophy className="w-4 h-4 text-black" />
                  </div>
                  <div>
                    <h3 className="font-black text-transparent bg-clip-text bg-gradient-to-r from-amber-200 via-yellow-300 to-amber-400 text-sm tracking-wide uppercase font-serif">
                      SẢNH VINH DANH GENTING VIP
                    </h3>
                    <p className="text-[10px] text-amber-400/70 font-mono tracking-wider">
                      DỮ LIỆU BÀN CƯỢC THỜI GIAN THỰC
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-amber-500/15 border border-amber-500/40 text-amber-300 text-[10px] font-mono shadow-xs">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                    <span>💱 {exchangeRateFormatted}</span>
                  </div>
                  <button
                    onClick={fetchBackendData}
                    disabled={isLoadingBackend}
                    title="Làm mới dữ liệu từ server"
                    className="w-7 h-7 rounded-full bg-amber-500/10 border border-amber-500/40 hover:bg-amber-500/20 text-amber-300 flex items-center justify-center transition-all cursor-pointer active:scale-95"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${isLoadingBackend ? "animate-spin text-amber-400" : ""}`} />
                  </button>
                  <button
                    onClick={() => setShowTopWins(false)}
                    className="w-7 h-7 rounded-full bg-stone-900 border border-stone-700 hover:border-red-500/60 text-slate-300 hover:text-red-400 flex items-center justify-center transition-all cursor-pointer font-bold active:scale-95"
                  >
                    ✕
                  </button>
                </div>
              </div>

              {/* Navigation Tabs */}
              <div className="flex items-center gap-2 px-5 pt-2.5 pb-2 bg-black/40 border-b border-amber-500/20">
                <button
                  onClick={() => setTopWinsTab("LEADERBOARD")}
                  className={`flex-1 py-1.5 px-3 rounded-xl text-[11px] font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                    topWinsTab === "LEADERBOARD"
                      ? "bg-gradient-to-r from-amber-500 to-yellow-600 text-black shadow-[0_2px_10px_rgba(245,158,11,0.4)]"
                      : "text-amber-200/70 hover:text-amber-200 hover:bg-white/5"
                  }`}
                >
                  <Crown className="w-3.5 h-3.5" />
                  BẢNG VÀNG
                </button>
                <button
                  onClick={() => setTopWinsTab("MY_BETS")}
                  className={`flex-1 py-1.5 px-3 rounded-xl text-[11px] font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                    topWinsTab === "MY_BETS"
                      ? "bg-gradient-to-r from-amber-500 to-yellow-600 text-black shadow-[0_2px_10px_rgba(245,158,11,0.4)]"
                      : "text-amber-200/70 hover:text-amber-200 hover:bg-white/5"
                  }`}
                >
                  <History className="w-3.5 h-3.5" />
                  CƯỢC CỦA TÔI
                  {sessionBetLogs.length > 0 && (
                    <span className="w-4 h-4 rounded-full bg-red-500 text-white text-[9px] flex items-center justify-center font-mono">
                      {sessionBetLogs.length}
                    </span>
                  )}
                </button>
                <button
                  onClick={() => setTopWinsTab("STATS")}
                  className={`flex-1 py-1.5 px-3 rounded-xl text-[11px] font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                    topWinsTab === "STATS"
                      ? "bg-gradient-to-r from-amber-500 to-yellow-600 text-black shadow-[0_2px_10px_rgba(245,158,11,0.4)]"
                      : "text-amber-200/70 hover:text-amber-200 hover:bg-white/5"
                  }`}
                >
                  <TrendingUp className="w-3.5 h-3.5" />
                  SOI CẦU BÀN
                </button>
              </div>

              {/* Tab Content Area */}
              <div 
                className="p-4 overflow-y-auto max-h-[310px] space-y-2.5"
                style={{ scrollbarWidth: 'thin', scrollbarColor: '#d97706 rgba(0,0,0,0.4)' }}
              >
                {/* TAB 1: LEADERBOARD */}
                {topWinsTab === "LEADERBOARD" && (
                  <div className="space-y-2.5">
                    {/* Top 1 Grand Champion */}
                    <div className="relative p-3 rounded-2xl bg-gradient-to-r from-amber-950/80 via-yellow-900/30 to-amber-950/80 border-2 border-amber-400/80 shadow-[0_4px_16px_rgba(245,158,11,0.25)] flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div className="relative w-10 h-10 rounded-full bg-gradient-to-b from-amber-300 to-yellow-600 p-[2px] shadow-lg">
                          <div className="w-full h-full rounded-full bg-black/80 flex items-center justify-center">
                            <Crown className="w-4 h-4 text-amber-300 drop-shadow animate-pulse" />
                          </div>
                          <span className="absolute -top-1 -right-1 text-[8px] font-black bg-red-600 text-white px-1 rounded-full border border-white">
                            #1
                          </span>
                        </div>
                        <div>
                          <div className="flex items-center gap-1.5">
                            <span className="font-black text-amber-200 text-xs">
                              halamlaicuocdo
                            </span>
                            <span className="text-[8px] font-bold px-1.5 py-0.2 rounded-sm bg-red-950 text-red-300 border border-red-500/40">
                              TỨ ĐỎ (1:16)
                            </span>
                          </div>
                          <p className="text-[9px] text-amber-400/70 font-mono">
                            Nổ hũ Thần Long VIP
                          </p>
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="text-sm font-mono font-black text-[#ffea79] drop-shadow">
                          +128.500.000 đ
                        </span>
                        <p className="text-[8px] text-emerald-400 font-bold">● Vừa thắng</p>
                      </div>
                    </div>

                    {/* Top 2 & Top 3 */}
                    <div className="grid grid-cols-2 gap-2.5">
                      <div className="p-2.5 rounded-xl bg-black/50 border border-slate-400/40 flex flex-col justify-between">
                        <div className="flex items-center justify-between mb-0.5">
                          <span className="text-[10px] font-black text-slate-300 flex items-center gap-1">
                            🥈 #2 hoang277056
                          </span>
                          <span className="text-[8px] font-bold px-1 rounded bg-slate-800 text-slate-200">
                            Tứ Trắng
                          </span>
                        </div>
                        <span className="text-xs font-mono font-black text-amber-300">
                          +85.200.000 đ
                        </span>
                      </div>

                      <div className="p-2.5 rounded-xl bg-black/50 border border-amber-700/50 flex flex-col justify-between">
                        <div className="flex items-center justify-between mb-0.5">
                          <span className="text-[10px] font-black text-amber-600 flex items-center gap-1">
                            🥉 #3 sangcute
                          </span>
                          <span className="text-[8px] font-bold px-1 rounded bg-amber-950 text-amber-300">
                            Bệt Chẵn
                          </span>
                        </div>
                        <span className="text-xs font-mono font-black text-amber-300">
                          +42.100.000 đ
                        </span>
                      </div>
                    </div>

                    {/* Session Leaderboard (Simulated VIPs + You) */}
                    <div 
                      className="space-y-1.5 pt-1 max-h-[155px] overflow-y-auto pr-1"
                      style={{ scrollbarWidth: 'thin', scrollbarColor: '#d97706 rgba(0,0,0,0.4)' }}
                    >
                      <span className="sticky top-0 bg-[#140b05]/95 backdrop-blur-xs py-0.5 z-10 text-[10px] font-bold tracking-wider text-amber-400 uppercase font-mono block">
                        ● Đại gia thắng lớn trong bàn này
                      </span>
                      {activeBots.map((p, idx) => (
                        <div
                          key={p.id}
                          className="flex items-center justify-between px-3 py-1.5 rounded-xl bg-black/40 border border-amber-500/15 hover:border-amber-500/40 transition-colors"
                        >
                          <div className="flex items-center gap-2">
                            <span className="text-[10px] font-bold font-mono text-amber-400/80 w-4">
                              #{idx + 4}
                            </span>
                            <span className="font-semibold text-slate-200 text-[11px]">
                              {p.name}
                            </span>
                          </div>
                          <span className="font-mono font-bold text-emerald-400 text-[11px]">
                            +{((idx + 1) * 3500 + 4200).toLocaleString()}K đ
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* TAB 2: MY BETS (CONNECT REAL BACKEND) */}
                {topWinsTab === "MY_BETS" && (
                  <div className="space-y-2">
                    {isLoadingBackend ? (
                      <div className="py-8 flex flex-col items-center justify-center text-amber-400 gap-2">
                        <RefreshCw className="w-5 h-5 animate-spin" />
                        <span className="text-[11px]">Đang đồng bộ từ máy chủ Genting...</span>
                      </div>
                    ) : sessionBetLogs.length === 0 && backendBets.length === 0 ? (
                      <div className="py-8 text-center text-slate-400 space-y-1.5">
                        <p className="text-xs font-semibold text-slate-300">
                          Chưa có lịch sử cược nào!
                        </p>
                        <p className="text-[10px] text-slate-500">
                          Hãy đặt cược vào các cửa Chẵn/Lẻ/4 Vị để bắt đầu ghi danh bảng vàng nhé.
                        </p>
                      </div>
                    ) : (
                      <div className="space-y-1.5">
                        {/* Live Session Bets (Realtime from current table session) */}
                        {sessionBetLogs.map((b) => (
                          <div
                            key={b.id}
                            className={`p-2 rounded-xl border flex items-center justify-between transition-all ${
                              b.won
                                ? "bg-emerald-950/40 border-emerald-500/40"
                                : "bg-black/50 border-amber-500/15"
                            }`}
                          >
                            <div>
                              <div className="flex items-center gap-2">
                                <span className="font-bold text-[11px] text-amber-300">
                                  {b.zoneName}
                                </span>
                                <span className="text-[9px] font-mono text-slate-400">
                                  Vòng #{b.roundSeq}
                                </span>
                                <span className="text-[9px] text-slate-500 font-mono">
                                  {b.time}
                                </span>
                              </div>
                              <span className="text-[10px] text-slate-300 font-mono">
                                Tiền cược: {(b.stake / 1000).toLocaleString()}K đ
                              </span>
                            </div>

                            <div className="text-right">
                              {b.won ? (
                                <span className="font-mono font-black text-emerald-300 text-xs flex items-center gap-1">
                                  +{(b.payout / 1000).toLocaleString()}K đ
                                </span>
                              ) : (
                                <span className="font-mono font-bold text-slate-500 text-[11px]">
                                  -{(b.stake / 1000).toLocaleString()}K đ
                                </span>
                              )}
                              <span
                                className={`text-[8px] font-bold uppercase ${
                                  b.won ? "text-emerald-400" : "text-slate-400"
                                }`}
                              >
                                {b.won ? "THẮNG" : "HOÀN TẤT"}
                              </span>
                            </div>
                          </div>
                        ))}

                        {/* Backend Bets from API */}
                        {backendBets.map((bb) => (
                          <div
                            key={bb.id}
                            className="p-2 rounded-xl bg-black/40 border border-slate-700/50 flex items-center justify-between"
                          >
                            <div>
                              <div className="flex items-center gap-1.5">
                                <span className="font-bold text-[11px] text-slate-200">
                                  {bb.betType}
                                </span>
                                <span className="text-[9px] font-mono text-slate-400">
                                  {new Date(bb.createdAt).toLocaleTimeString()}
                                </span>
                              </div>
                              <span className="text-[10px] font-mono text-slate-300">
                                Cược: {parseFloat(bb.stake).toLocaleString()} đ
                              </span>
                            </div>
                            <div className="text-right">
                              <span
                                className={`font-mono font-bold text-xs ${
                                  parseFloat(bb.payout) > 0
                                    ? "text-emerald-400"
                                    : "text-slate-500"
                                }`}
                              >
                                {parseFloat(bb.payout) > 0
                                  ? `+${parseFloat(bb.payout).toLocaleString()} đ`
                                  : `-${parseFloat(bb.stake).toLocaleString()} đ`}
                              </span>
                              <p className="text-[8px] font-mono text-slate-400 uppercase">
                                {bb.status}
                              </p>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* TAB 3: ROADMAP & STATS */}
                {topWinsTab === "STATS" && (
                  <div className="space-y-3">
                    {/* Stats Grid */}
                    {(() => {
                      const total = history.length;
                      const evens = history.filter((h) => h.isEven).length;
                      const odds = total - evens;
                      const evenPct = total > 0 ? Math.round((evens / total) * 100) : 50;
                      const oddPct = 100 - evenPct;

                      return (
                        <>
                          <div className="grid grid-cols-3 gap-2 text-center">
                            <div className="p-2 rounded-xl bg-black/50 border border-amber-500/20">
                              <span className="text-[10px] text-slate-400 block">Tổng số ván</span>
                              <span className="text-sm font-mono font-black text-amber-300">
                                {total} ván
                              </span>
                            </div>
                            <div className="p-2 rounded-xl bg-black/50 border border-yellow-500/40">
                              <span className="text-[10px] text-yellow-300 block">Tỷ lệ Chẵn</span>
                              <span className="text-sm font-mono font-black text-yellow-400">
                                {evenPct}% ({evens})
                              </span>
                            </div>
                            <div className="p-2 rounded-xl bg-black/50 border border-rose-500/40">
                              <span className="text-[10px] text-rose-300 block">Tỷ lệ Lẻ</span>
                              <span className="text-sm font-mono font-black text-rose-400">
                                {oddPct}% ({odds})
                              </span>
                            </div>
                          </div>

                          {/* Visual Road Ribbon */}
                          <div className="p-3 rounded-2xl bg-black/60 border border-amber-500/20">
                            <span className="text-[10px] font-bold text-amber-300 uppercase tracking-wider block mb-2 font-mono">
                              ● 24 VÁN GẦN NHẤT
                            </span>
                            <div className="flex flex-wrap gap-1.5 max-h-[120px] overflow-y-auto">
                              {history.slice(-24).map((h, i) => (
                                <div
                                  key={i}
                                  className={`w-6 h-6 rounded-full flex items-center justify-center font-black text-[10px] shadow-sm border ${
                                    h.isEven
                                      ? "bg-yellow-400 border-yellow-200 text-black"
                                      : "bg-rose-600 border-rose-300 text-white"
                                  }`}
                                  title={`Vòng #${h.seq}: ${h.isEven ? "Chẵn" : "Lẻ"} (${h.redCount} Đỏ)`}
                                >
                                  {h.isEven ? "C" : "L"}
                                </div>
                              ))}
                            </div>
                          </div>
                        </>
                      );
                    })()}
                  </div>
                )}
              </div>

              {/* Bottom Footer - Real User Account Card (Connected Backend) */}
              <div className="px-4 py-2.5 bg-gradient-to-r from-[#170e06] via-[#201207] to-[#170e06] border-t border-amber-500/30 flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-full bg-gradient-to-br from-amber-400 to-amber-700 p-[1.5px]">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      src="/games/xocdia/assets_hd/avatar_1.png"
                      alt="Avatar"
                      className="w-full h-full rounded-full object-cover"
                    />
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5">
                      <span className="font-bold text-amber-200 text-[11px]">
                        {realUsername}
                      </span>
                      <span className="text-[8px] font-black px-1.5 py-0.2 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/40 font-mono">
                        VIP
                      </span>
                    </div>
                    <span className="text-[10px] text-slate-400 font-mono">
                      Số dư:{" "}
                      <strong className="text-amber-300 font-bold">
                        {balanceLabel}
                      </strong>{" "}
                      <span className="text-amber-400/80 text-[9px]">
                        (≈ {balanceUsdLabel} USD)
                      </span>
                    </span>
                  </div>
                </div>

                <div className="text-right">
                  <span className="text-[10px] text-slate-400 block">Thắng phiên này:</span>
                  <span className="text-xs font-mono font-black text-emerald-300">
                    +{sessionTotalWon.toLocaleString()} đ
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ========================================================= */}
        {/* LAYER 8: IN-GAME LIVE CHAT ROOM & TROLL AI BOT MODERATOR */}
        {/* ========================================================= */}
        {showChat && (
          <div className="absolute right-[12px] top-[12px] bottom-[12px] w-[315px] z-50 rounded-2xl bg-gradient-to-b from-[#191009]/96 via-[#0f0704]/97 to-[#080302]/98 border-2 border-amber-500/70 shadow-[0_10px_35px_rgba(0,0,0,0.95),0_0_30px_rgba(245,158,11,0.25)] flex flex-col backdrop-blur-md overflow-hidden animate-in fade-in slide-in-from-right duration-200">
            {/* 8.1 Header */}
            <div className="px-3 py-2.5 bg-gradient-to-r from-amber-950/90 via-[#261609]/95 to-[#120803]/95 border-b border-amber-500/40 flex items-center justify-between shrink-0">
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-full bg-amber-500/20 border border-amber-400/50 flex items-center justify-center text-amber-300 shadow-sm">
                  <MessageSquare className="w-3.5 h-3.5 text-amber-300" />
                </div>
                <div>
                  <div className="flex items-center gap-1.5">
                    <span className="font-black text-amber-200 text-[11px] tracking-wide uppercase">
                      CHAT BÀN VIP #{roundSeq}
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
                    <span className="text-[9px] text-emerald-400 font-mono">9 người online</span>
                    <span className="text-[8px] text-amber-400/70">| Bot Bảo Kê trực</span>
                  </div>
                </div>
              </div>

              <button
                onClick={() => setShowChat(false)}
                className="w-6 h-6 rounded-full flex items-center justify-center text-slate-400 hover:text-white hover:bg-white/10 active:scale-90 transition-all cursor-pointer"
                title="Đóng phòng chat"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* 8.2 Scrollable Message List */}
            <div className="flex-1 overflow-y-auto p-2.5 space-y-2 select-text">
              {chatMessages.map((msg) => {
                if (msg.role === "BOT") {
                  return (
                    <div key={msg.id} className="flex flex-col gap-0.5 items-start">
                      <div className="flex items-center gap-1.5">
                        <span className="px-1.5 py-0.5 rounded bg-cyan-950/90 border border-cyan-400/60 text-[9px] font-black text-cyan-300 flex items-center gap-1 shadow-[0_0_8px_rgba(6,182,212,0.4)]">
                          <Bot className="w-2.5 h-2.5 text-cyan-400 animate-pulse" />
                          AI BẢO KÊ GENTING 🤖
                        </span>
                        <span className="text-[8px] text-slate-500 font-mono">{msg.time}</span>
                      </div>
                      <div className="p-2 rounded-xl bg-gradient-to-br from-cyan-950/70 via-[#071d2b]/85 to-[#020d14]/95 border border-cyan-500/50 text-cyan-100 text-[11px] leading-relaxed shadow-lg backdrop-blur-sm max-w-[95%] font-medium">
                        {msg.text}
                      </div>
                    </div>
                  );
                }

                if (msg.role === "DEALER") {
                  return (
                    <div key={msg.id} className="flex flex-col gap-0.5 items-start">
                      <div className="flex items-center gap-1.5">
                        <span className="px-1.5 py-0.5 rounded bg-pink-950/90 border border-pink-400/60 text-[9px] font-black text-pink-300 flex items-center gap-1 shadow-[0_0_8px_rgba(244,114,182,0.4)]">
                          DEALER VY VY 💋
                        </span>
                        <span className="text-[8px] text-slate-500 font-mono">{msg.time}</span>
                      </div>
                      <div className="p-2 rounded-xl bg-gradient-to-br from-pink-950/70 via-[#260a17]/85 to-[#12030a]/95 border border-pink-500/50 text-pink-100 text-[11px] leading-relaxed shadow-lg backdrop-blur-sm max-w-[95%]">
                        {msg.text}
                      </div>
                    </div>
                  );
                }

                if (msg.role === "USER") {
                  return (
                    <div key={msg.id} className="flex flex-col gap-0.5 items-end">
                      <div className="flex items-center gap-1.5">
                        <span className="text-[8px] text-slate-500 font-mono">{msg.time}</span>
                        <span className="px-1.5 py-0.5 rounded bg-amber-950/90 border border-amber-400/60 text-[9px] font-black text-amber-300">
                          {realUsername} (VIP)
                        </span>
                      </div>
                      <div className="p-2 rounded-xl bg-gradient-to-br from-amber-950/80 via-[#2a1707]/85 to-[#120802]/95 border border-amber-500/60 text-amber-100 text-[11px] leading-relaxed shadow-lg backdrop-blur-sm max-w-[90%]">
                        {msg.text}
                      </div>
                    </div>
                  );
                }

                // Ambient Table Player
                return (
                  <div key={msg.id} className="flex flex-col gap-0.5 items-start">
                    <div className="flex items-center gap-1.5">
                      {msg.avatar && (
                        /* eslint-disable-next-line @next/next/no-img-element */
                        <img
                          src={msg.avatar}
                          alt={msg.sender}
                          className="w-3.5 h-3.5 rounded-full object-cover border border-amber-500/40"
                        />
                      )}
                      <span className="text-[10px] font-bold text-slate-300">{msg.sender}</span>
                      <span className="text-[8px] text-slate-500 font-mono">{msg.time}</span>
                    </div>
                    <div className="p-2 rounded-xl bg-white/[0.07] border border-white/10 text-slate-200 text-[11px] leading-relaxed max-w-[90%]">
                      {msg.text}
                    </div>
                  </div>
                );
              })}
              <div ref={chatEndRef} />
            </div>

            {/* 8.3 Quick Chat Chips Carousel */}
            <div className="px-2 py-1.5 border-t border-amber-500/20 bg-black/40 flex items-center gap-1.5 overflow-x-auto shrink-0">
              {QUICK_CHATS.map((qc, idx) => (
                <button
                  key={idx}
                  disabled={isMutedByBot}
                  onClick={() => handleSendMessage(qc)}
                  className="shrink-0 px-2 py-1 rounded-full bg-white/[0.08] hover:bg-amber-500/20 border border-amber-500/30 text-amber-200 hover:text-amber-100 text-[10px] font-medium transition-all cursor-pointer whitespace-nowrap active:scale-95 disabled:opacity-30 disabled:cursor-not-allowed"
                >
                  {qc}
                </button>
              ))}
            </div>

            {/* 8.4 Input Form or Locked Mute Banner */}
            {isMutedByBot ? (
              <div className="p-2.5 bg-red-950/90 border-t-2 border-red-500/80 flex items-center justify-center gap-2 text-red-200 text-[11px] font-bold animate-pulse shrink-0">
                <ShieldAlert className="w-4 h-4 text-red-400 shrink-0" />
                <span>[THẺ ĐỎ] Đang bị Bot phạt tĩnh tâm ({muteSecondsLeft}s)... 🤐</span>
              </div>
            ) : (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  handleSendMessage(chatInput);
                }}
                className="p-2 bg-[#0a0503]/95 border-t border-amber-500/30 flex items-center gap-1.5 shrink-0"
              >
                <input
                  type="text"
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  placeholder="Nhập chat... (Cấm chửi thề bot kick)"
                  maxLength={100}
                  className="flex-1 bg-black/80 border border-amber-500/40 rounded-xl px-2.5 py-1.5 text-xs text-amber-100 placeholder-slate-500 focus:outline-none focus:border-amber-400 focus:ring-1 focus:ring-amber-400 transition-all font-sans"
                />
                <button
                  type="submit"
                  disabled={!chatInput.trim()}
                  className="w-8 h-8 rounded-xl bg-gradient-to-br from-amber-500 to-amber-700 hover:from-amber-400 hover:to-amber-600 disabled:opacity-40 disabled:cursor-not-allowed text-black font-bold flex items-center justify-center shadow-lg transition-all active:scale-95 cursor-pointer shrink-0"
                  title="Gửi tin nhắn"
                >
                  <Send className="w-3.5 h-3.5" />
                </button>
              </form>
            )}
          </div>
        )}

        {/* ========================================================= */}
        {/* JACKPOT TU QUY WIN OVERLAY */}
        {/* ========================================================= */}
        {jackpotWin && (
          <div className="absolute inset-0 z-[70] flex items-center justify-center pointer-events-none">
            <div className="absolute inset-0 bg-black/60 backdrop-blur-[1px]" />
            <div className="relative flex flex-col items-center gap-2 px-8 py-5 rounded-3xl bg-gradient-to-b from-[#2a0d16]/95 via-[#12060b]/95 to-black/95 border-2 border-amber-400/80 shadow-[0_0_60px_rgba(245,158,11,0.55),0_0_120px_rgba(220,20,60,0.35)] animate-in fade-in zoom-in-95 duration-300">
              <div className="flex items-center gap-2">
                <Crown className="w-5 h-5 text-amber-300 animate-bounce" />
                <span className="text-[13px] font-black tracking-[0.2em] text-amber-300 uppercase">
                  Jackpot No Hu
                </span>
                <Crown className="w-5 h-5 text-amber-300 animate-bounce" />
              </div>
              <div className="flex items-center gap-2">
                {jackpotWin.dice.map((d, i) => (
                  <RubyDice key={`win-${i}`} value={d} size={34} />
                ))}
              </div>
              <div className="text-[11px] font-bold text-rose-200 tracking-wider uppercase">
                Tu Quy {jackpotWin.door}
              </div>
              <div className="text-[13px] font-black text-amber-200 tracking-wide">
                {jackpotWin.winnerName}
                {jackpotWin.isMine && (
                  <span className="ml-2 px-2 py-0.5 rounded-full bg-amber-400 text-black text-[10px] uppercase">
                    Ban trung hu
                  </span>
                )}
              </div>
              <div className="text-[26px] font-mono font-black text-[#ffea79] drop-shadow-[0_2px_10px_rgba(0,0,0,0.9)]">
                +{jackpotWin.amount.toLocaleString()} đ
              </div>
              <div className="text-[10px] text-amber-200/70">
                {jackpotWin.isMine ? "Tien da ve vi cua ban" : "Tien ve vi nguoi trung — ca ban cung thay"}
              </div>
              <button
                onClick={() => setJackpotWin(null)}
                className="pointer-events-auto mt-1 px-4 py-1.5 rounded-full bg-gradient-to-r from-amber-400 to-amber-600 text-black text-[11px] font-black uppercase tracking-wider hover:brightness-110 active:scale-95 transition-all cursor-pointer"
              >
                {jackpotWin.isMine ? "Nhan thuong" : "Dong"}
              </button>
            </div>
          </div>
        )}
        {jackpotWin && (
          <JackpotCoinShower
            active={!!jackpotWin}
            winnerName={jackpotWin.winnerName}
            amount={jackpotWin.amount}
            targetX={50}
            targetY={jackpotWin.isMine ? 88 : 40}
          />
        )}

        {/* ========================================================= */}
        {/* LUXURY CASINO FLOATING TOAST NOTIFICATION */}
        {/* ========================================================= */}
        {toastMsg && (
          <div className="absolute top-[22px] left-1/2 -translate-x-1/2 z-60 pointer-events-none transition-all duration-300">
            <div className="flex items-center gap-2.5 px-5 py-2 rounded-full bg-gradient-to-r from-red-950/95 via-[#230909]/95 to-red-950/95 border-2 border-red-500/80 shadow-[0_0_25px_rgba(239,68,68,0.85),0_8px_20px_rgba(0,0,0,0.95)] backdrop-blur-md animate-bounce">
              <AlertTriangle className="w-4 h-4 text-yellow-400 shrink-0 animate-pulse" />
              <span className="text-[12px] font-bold text-yellow-100 tracking-wide drop-shadow-[0_1px_3px_rgba(0,0,0,0.95)]">
                {toastMsg}
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default XocDiaLandscapeGame;
