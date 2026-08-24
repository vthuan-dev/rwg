"use client";

import React, { useEffect, useState, useCallback, useMemo, useRef } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import { PasswordField, TextField } from "@/components/profile/FormField";
import { useTranslation } from "@/context/LanguageContext";
import {
  ApiError,
  bankAccounts,
  banks,
  findBank,
  getPlayerToken,
  withdraw,
  walletMe,
  me,
  verifyWithdrawalPassword,
  type BankAccount,
  type BankOption,
  type PaymentOrder,
  type Wallet,
  type UserResponse,
} from "@/lib/playerApi";
import { History, AlertCircle, CheckCircle, Plus, Check, X, Loader2 } from "lucide-react";

/**
 * Số tiền tối thiểu mỗi lệnh rút, khớp `rwg.withdrawal.min-amount` ở backend.
 */
const MIN_WITHDRAW_AMOUNT = 20;

/**
 * Tổng hạn mức rút trong một ngày (UTC), khớp `rwg.withdrawal.daily-max-amount` ở backend.
 *
 * Đây là TỔNG của cả ngày, không phải giới hạn mỗi lệnh: rút hai lần $3,000 sẽ bị chặn ở
 * lệnh thứ hai dù từng lệnh đều dưới hạn mức.
 */
const DAILY_MAX_AMOUNT = 5000;

/**
 * Độ dài tối thiểu của mật khẩu rút tiền, khớp {@code @Size(min = 6)} của
 * `SetWithdrawalPasswordRequest` ở backend.
 *
 * Chỉ gọi kiểm khi đã đủ số ký tự này: mỗi lần kiểm sai ăn một lượt trong bộ đếm chống dò
 * dùng chung với lệnh rút, nên gọi khi người dùng mới gõ 2-3 ký tự là tự đốt ngân sách đó.
 */
const MIN_PASSWORD_LENGTH = 6;

/**
 * Thời gian chờ sau khi người dùng ngừng gõ mới gọi kiểm (ms).
 *
 * Không debounce thì người dùng đặt mật khẩu dài hơn 6 ký tự sẽ bị gọi kiểm ngay khi gõ tới
 * ký tự thứ 6 — báo sai oan và trừ mất một lượt thử dù họ đang gõ đúng.
 */
const VERIFY_DEBOUNCE_MS = 700;

/**
 * Trạng thái kiểm ngầm mật khẩu rút tiền.
 *
 * `locked` tách riêng khỏi `invalid`: bị khoá tạm thì gõ đúng cũng không được, nên giao diện
 * phải nói rõ là chờ hết thời gian khoá thay vì để người dùng gõ lại vô ích.
 */
type PasswordCheckState = "idle" | "checking" | "valid" | "invalid" | "locked";

export default function WithdrawPage() {
  const router = useRouter();
  const { t } = useTranslation();

  const [checked, setChecked] = useState(false);
  const [loading, setLoading] = useState(true);
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [currentUser, setCurrentUser] = useState<UserResponse | null>(null);
  const [accounts, setAccounts] = useState<BankAccount[]>([]);
  const [bankList, setBankList] = useState<BankOption[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<string>("");

  const [amount, setAmount] = useState("");
  const [withdrawalPassword, setWithdrawalPassword] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successOrder, setSuccessOrder] = useState<PaymentOrder | null>(null);

  // ===== Kiểm ngầm mật khẩu rút tiền =====
  const [passwordCheck, setPasswordCheck] = useState<PasswordCheckState>("idle");
  const [attemptsRemaining, setAttemptsRemaining] = useState<number | null>(null);
  const [lockedSeconds, setLockedSeconds] = useState<number | null>(null);

  /**
   * Mật khẩu đã kiểm gần nhất, kèm kết quả.
   *
   * Có cái này thì xoá một ký tự rồi gõ lại đúng ký tự đó KHÔNG gọi kiểm lần nữa. Không cache
   * thì mỗi lần sửa qua sửa lại đều tốn một lượt trong bộ đếm chống dò dùng chung với lệnh rút.
   *
   * Dùng `useRef` chứ không `useState`: đây là bộ nhớ đệm giữa các lần chạy effect, thay đổi nó
   * không nên kéo theo một lần render mới.
   */
  const verifiedCache = useRef<Map<string, boolean>>(new Map());

  // Load ban đầu: số dư ví, thông tin user (để xem đã đặt mật khẩu rút chưa), và danh sách STK ngân hàng
  const loadData = useCallback(async () => {
    try {
      const [walletData, userData, accountsData, banksData] = await Promise.all([
        walletMe(),
        me(),
        bankAccounts(),
        banks(),
      ]);

      setWallet(walletData);
      setCurrentUser(userData);
      setAccounts(accountsData);
      setBankList(banksData);

      // Tự chọn tài khoản mặc định
      const defaultAcc = accountsData.find((a) => a.isDefault && a.status === "ACTIVE");
      if (defaultAcc) {
        setSelectedAccountId(defaultAcc.id);
      } else if (accountsData.length > 0) {
        setSelectedAccountId(accountsData[0].id);
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        router.replace("/login");
      } else {
        setError(t("withdraw.err_failed"));
      }
    } finally {
      setLoading(false);
    }
  }, [router, t]);

  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }
    setChecked(true);
    void loadData();
  }, [router, loadData]);

  /**
   * Kiểm ngầm mật khẩu rút tiền sau khi người dùng ngừng gõ.
   *
   * Ba lớp chặn để không đốt bộ đếm chống dò (10 lần sai / 15 phút, DÙNG CHUNG với lệnh rút):
   *   1. Bỏ qua khi chưa đủ `MIN_PASSWORD_LENGTH` ký tự.
   *   2. Chờ `VERIFY_DEBOUNCE_MS` sau ký tự cuối cùng.
   *   3. Đọc `verifiedCache` trước — cùng một chuỗi không bao giờ gọi API hai lần.
   *
   * Cờ `cancelled` chặn cập nhật state của một request đã cũ: người dùng gõ tiếp trong lúc chờ
   * mạng thì kết quả của mật khẩu ngắn hơn không được phép ghi đè trạng thái hiện tại.
   */
  useEffect(() => {
    if (!currentUser?.hasWithdrawalPassword) return;

    if (withdrawalPassword.length < MIN_PASSWORD_LENGTH) {
      setPasswordCheck("idle");
      setAttemptsRemaining(null);
      return;
    }

    const cached = verifiedCache.current.get(withdrawalPassword);
    if (cached !== undefined) {
      setPasswordCheck(cached ? "valid" : "invalid");
      return;
    }

    setPasswordCheck("checking");
    let cancelled = false;

    const timer = setTimeout(async () => {
      try {
        const result = await verifyWithdrawalPassword(withdrawalPassword);
        if (cancelled) return;

        verifiedCache.current.set(withdrawalPassword, result.valid);
        setPasswordCheck(result.valid ? "valid" : "invalid");
        setAttemptsRemaining(result.valid ? null : result.attemptsRemaining);
      } catch (err) {
        if (cancelled) return;

        // Bị khoá tạm: gõ đúng cũng không qua được, nên KHÔNG cache kết quả này — hết thời gian
        // khoá thì cùng mật khẩu đó phải được kiểm lại từ đầu.
        if (err instanceof ApiError && (err.status === 429 || err.status === 423)) {
          setPasswordCheck("locked");
          setLockedSeconds(Number(err.details?.retryAfterSeconds) || null);
          return;
        }
        // Lỗi mạng hoặc lỗi khác: về `idle` chứ không báo sai mật khẩu. Báo sai cho một lỗi
        // mạng sẽ khiến người dùng đi sửa một mật khẩu vốn đã đúng.
        setPasswordCheck("idle");
      }
    }, VERIFY_DEBOUNCE_MS);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [withdrawalPassword, currentUser?.hasWithdrawalPassword]);

  // Rút toàn bộ số dư khả dụng
  const handleWithdrawAll = useCallback(() => {
    if (wallet) {
      // Số dư là chuỗi ví dụ "100.00" -> loại bỏ phần thập phân bằng 0 hoặc giữ nguyên
      const balanceVal = parseFloat(wallet.balance);
      if (balanceVal > 0) {
        setAmount(String(Math.floor(balanceVal)));
      }
    }
  }, [wallet]);

  /** Số dư khả dụng dạng số; `0` khi chưa tải xong ví. */
  const balanceValue = useMemo(() => {
    return wallet ? parseFloat(wallet.balance) : 0;
  }, [wallet]);

  /**
   * Số tiền lớn nhất được phép nhập — phần nguyên của số dư.
   *
   * Làm tròn XUỐNG chứ không làm tròn thông thường: số dư `50.99` mà cho nhập `51` thì
   * backend từ chối vì không đủ số dư.
   */
  const maxWithdrawable = useMemo(() => Math.floor(balanceValue), [balanceValue]);

  /**
   * Nhận số tiền người dùng gõ, KHÔNG cho vượt số dư.
   *
   * Chặn ngay tại ô nhập thay vì để họ gõ `500` với số dư `50`: khi đó bảng tóm tắt bên
   * dưới hiển "Số tiền rút $500" và "Số dư còn lại $0.00" — hai con số trông như một giao
   * dịch hợp lệ, trong khi thực tế lệnh không thể gửi được.
   *
   * Lọc sạch ký tự không phải số: `inputMode="numeric"` chỉ gợi ý bàn phím trên điện thoại,
   * nó không ngăn được ai dán chữ vào hay gõ từ bàn phím máy tính.
   */
  const handleAmountChange = useCallback((raw: string) => {
    const digitsOnly = raw.replace(/[^\d]/g, "");
    if (digitsOnly === "") {
      setAmount("");
      return;
    }
    const parsed = parseInt(digitsOnly, 10);
    setAmount(String(Math.min(parsed, maxWithdrawable)));
  }, [maxWithdrawable]);

  /**
   * Các mốc tiền gợi ý, TÍNH THEO SỐ DƯ THẬT.
   *
   * Trước đây danh sách này cố định `[100, 500, 1000]`, nên người có số dư $50 thấy cả ba nút
   * đều bị tắt — một hàng nút vô dụng chiếm chỗ và không giúp được gì.
   *
   * Cách chọn mốc:
   *   - Ưu tiên các mốc "tròn" quen mắt (20, 50, 100, 200, 500, 1000, 2000, 5000) nằm trong
   *     khoảng [tối thiểu, số dư] — người dùng nhận ra ngay con số mình muốn.
   *   - Nếu số mốc tròn lọt vào ít hơn 3 (số dư nhỏ, ví dụ $50 chỉ có 20 và 50), bù thêm các
   *     mốc theo tỷ lệ 25% / 50% / 75% số dư để hàng nút không bị trống.
   *   - Luôn giữ tối đa 3 nút: hàng còn một nút "Tối đa" nữa, bốn nút là vừa khít bề ngang
   *     điện thoại, thêm nữa thì chữ bị co lại.
   *
   * KHÔNG bao gồm chính số dư đầy đủ: nút "Tối đa" bên cạnh đã làm việc đó, hai nút cho cùng
   * một giá trị chỉ gây bối rối.
   */
  const quickAmounts = useMemo(() => {
    if (maxWithdrawable < MIN_WITHDRAW_AMOUNT) return [];

    const ROUND_STEPS = [20, 50, 100, 200, 500, 1000, 2000, 5000];
    const candidates = new Set<number>();

    for (const step of ROUND_STEPS) {
      if (step >= MIN_WITHDRAW_AMOUNT && step <= maxWithdrawable) {
        candidates.add(step);
      }
    }

    // Bù mốc theo tỷ lệ khi số dư nhỏ khiến các mốc tròn không đủ 3 lựa chọn.
    if (candidates.size < 3) {
      for (const ratio of [0.25, 0.5, 0.75]) {
        const value = Math.floor(maxWithdrawable * ratio);
        if (value >= MIN_WITHDRAW_AMOUNT && value < maxWithdrawable) {
          candidates.add(value);
        }
      }
    }

    // Lấy 3 mốc LỚN NHẤT rồi xếp tăng dần: người rút tiền thường nhắm mức gần hết số dư,
    // nên các mốc sát số dư hữu ích hơn mấy mốc nhỏ nhất.
    return Array.from(candidates)
      .sort((a, b) => a - b)
      .slice(-3);
  }, [maxWithdrawable]);

  // Đổi lỗi từ backend thành câu người dùng hiểu được
  const describeError = useCallback((err: unknown): string => {
    if (!(err instanceof ApiError)) {
      return t("withdraw.err_failed");
    }
    if (err.code === "WITHDRAWAL_PASSWORD_NOT_SET") {
      return t("withdraw.err_password_not_set");
    }
    if (err.code === "INVALID_CREDENTIALS") {
      return t("withdraw.err_wrong_password");
    }
    if (err.code === "INSUFFICIENT_BALANCE") {
      return t("withdraw.err_insufficient");
    }
    if (err.code === "WITHDRAWAL_LIMIT_EXCEEDED") {
      return t("withdraw.err_max", { max: DAILY_MAX_AMOUNT.toLocaleString("en-US") });
    }
    if (err.status === 423 || err.status === 429) {
      // Bị khoá tạm do gõ sai nhiều lần.
      // Tên trường phải là `retryAfterSeconds` — đó là khoá backend đặt trong
      // `Map.of("retryAfterSeconds", ...)`. Trước đây chỗ này đọc `secondsRemaining`
      // (không tồn tại) nên luôn rơi về mặc định 60 giây, báo sai thời gian chờ thật.
      const seconds = err.details?.retryAfterSeconds ?? "60";
      return t("withdraw.err_locked", { seconds });
    }
    return err.message || t("withdraw.err_failed");
  }, [t]);

  const handleWithdraw = useCallback(async () => {
    if (!selectedAccountId) {
      setError(t("withdraw.err_no_account"));
      return;
    }
    const parsedAmount = parseFloat(amount);
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      setError(t("withdraw.err_amount_invalid"));
      return;
    }
    if (parsedAmount < MIN_WITHDRAW_AMOUNT) {
      setError(t("withdraw.err_min", { min: String(MIN_WITHDRAW_AMOUNT) }));
      return;
    }
    if (wallet && parsedAmount > parseFloat(wallet.balance)) {
      setError(t("withdraw.err_insufficient"));
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      const order = await withdraw(amount, withdrawalPassword, selectedAccountId);
      setSuccessOrder(order);
    } catch (err) {
      // Mật khẩu bị từ chối ở bước gửi lệnh dù kiểm ngầm đã báo đúng: xảy ra khi admin reset
      // mật khẩu rút đúng trong lúc người chơi đang ở trang này. Phải xóa cache và tắt đèn nút,
      // không thì nút vẫn sáng và họ bấm lại — mỗi lần bấm ăn thêm một lượt chống dò.
      if (err instanceof ApiError && err.code === "INVALID_CREDENTIALS") {
        verifiedCache.current.delete(withdrawalPassword);
        setPasswordCheck("invalid");
      }
      setError(describeError(err));
    } finally {
      setSubmitting(false);
    }
  }, [amount, withdrawalPassword, selectedAccountId, wallet, describeError, t]);

  const selectedAccount = useMemo(() => {
    return accounts.find((a) => a.id === selectedAccountId);
  }, [accounts, selectedAccountId]);

  const remainingBalance = useMemo(() => {
    if (!wallet) return "0.00";
    const balanceVal = parseFloat(wallet.balance);
    const withdrawVal = parseFloat(amount) || 0;
    const rem = balanceVal - withdrawVal;
    return rem >= 0 ? rem.toFixed(2) : "0.00";
  }, [wallet, amount]);

  /** Số tiền đã hợp lệ: là số, đạt tối thiểu, và không vượt số dư. */
  const amountValid = useMemo(() => {
    const parsed = parseFloat(amount);
    if (isNaN(parsed) || parsed < MIN_WITHDRAW_AMOUNT) return false;
    if (wallet && parsed > parseFloat(wallet.balance)) return false;
    return true;
  }, [amount, wallet]);

  /**
   * Nút gửi lệnh chỉ sáng khi ĐỦ CẢ BA: có tài khoản nhận, số tiền hợp lệ, và mật khẩu rút
   * ĐÃ ĐƯỢC KIỂM LÀ ĐÚNG.
   */
  const canSubmit = useMemo(() => {
    return Boolean(selectedAccountId) && amountValid && passwordCheck === "valid";
  }, [selectedAccountId, amountValid, passwordCheck]);

  /**
   * Câu nói rõ ĐANG THIẾU GÌ để nút sáng lên.
   *
   * Nút bị làm mờ mà không giải thích lý do là chỗ người dùng mắc kẹt phổ biến nhất — họ
   * không biết mình cần sửa ô nào nên bấm liên tục vào nút không phản ứng.
   */
  const blockingHint = useMemo(() => {
    if (canSubmit) return null;
    if (!selectedAccountId) return t("withdraw.hint_need_account");
    if (!amountValid) return t("withdraw.hint_need_amount", { min: String(MIN_WITHDRAW_AMOUNT) });
    if (passwordCheck === "locked") return null; // đã có cảnh báo khoá riêng, không nói trùng
    return t("withdraw.hint_need_password");
  }, [canSubmit, selectedAccountId, amountValid, passwordCheck, t]);

  if (!checked || loading) {
    return (
      <MobileShell>
        <div className="flex h-64 items-center justify-center text-sm text-[#8b8b8b]">
          {t("asset.loading")}...
        </div>
      </MobileShell>
    );
  }

  // Màn hình thành công sau khi gửi lệnh
  if (successOrder) {
    return (
      <MobileShell
        header={
          <TopNavigationBar
            title={t("withdraw.title")}
            backHref="/profile"
          />
        }
      >
        <main className="flex grow flex-col px-5 py-8 text-center justify-between">
          <div className="flex flex-col items-center pt-8">
            <CheckCircle className="size-16 text-[#01bd8b] mb-4" />
            <h2 className="text-xl font-bold text-white mb-2">
              {t("withdraw.success_title")}
            </h2>
            <p className="text-sm text-[#8b8b8b] max-w-sm leading-relaxed">
              {t("withdraw.success_desc")}
            </p>

            <div className="mt-8 w-full bg-[#1f1f1f] p-4 flex flex-col gap-y-3 text-left">
              <div className="flex justify-between text-xs">
                <span className="text-[#8b8b8b]">{t("withdraw.order_id")}</span>
                <span className="font-mono text-white text-right break-all">{successOrder.id}</span>
              </div>
              <div className="flex justify-between text-xs border-t border-white/5 pt-3">
                <span className="text-[#8b8b8b]">{t("withdraw.summary_amount")}</span>
                <span className="font-bold text-white text-right">
                  ${parseFloat(successOrder.amount).toFixed(2)} {successOrder.currency}
                </span>
              </div>
              <div className="flex justify-between text-xs border-t border-white/5 pt-3">
                <span className="text-[#8b8b8b]">{t("withdraw.summary_account")}</span>
                <span className="text-white text-right">
                  {selectedAccount ? (
                    <>
                      {findBank(bankList, selectedAccount.bankCode)?.shortName || selectedAccount.bankCode}
                      {" · "}{selectedAccount.maskedAccountNumber}
                    </>
                  ) : "--"}
                </span>
              </div>
              <div className="flex justify-between text-xs border-t border-white/5 pt-3">
                <span className="text-[#8b8b8b]">{t("withdraw.status_pending")}</span>
                <span className="text-primary font-bold text-right">PENDING</span>
              </div>
            </div>
          </div>

          <div className="flex flex-col gap-y-3 mt-8">
            <Link
              href="/asset/withdraw/history"
              className="h-12 w-full bg-primary flex items-center justify-center text-[0.9375rem] font-bold text-white transition-opacity active:opacity-85"
            >
              {t("withdraw.view_history")}
            </Link>
            <Link
              href="/profile"
              className="h-12 w-full border border-white/10 flex items-center justify-center text-[0.9375rem] font-bold text-[#d0d5da] transition-colors hover:bg-white/5 active:bg-white/10"
            >
              {t("withdraw.back_to_profile")}
            </Link>
          </div>
        </main>
      </MobileShell>
    );
  }

  return (
    <MobileShell
      header={
        <TopNavigationBar
          title={t("withdraw.title")}
          backHref="/profile"
          rightSlot={
            <Link href="/asset/withdraw/history" className="flex items-center text-[#8b8b8b] hover:text-white transition-colors">
              <History className="size-5" />
            </Link>
          }
        />
      }
    >
      <main className="flex grow flex-col px-5 py-6">
        {/* Số dư khả dụng */}
        <div className="bg-[#1f1f1f] p-4 flex justify-between items-center mb-6">
          <div className="flex flex-col">
            <span className="text-xs text-[#8b8b8b]">{t("withdraw.available_balance")}</span>
            <span className="text-2xl font-bold text-white mt-1">
              ${wallet ? parseFloat(wallet.balance).toFixed(2) : "0.00"}
            </span>
          </div>
          <button
            onClick={handleWithdrawAll}
            className="px-3 py-1.5 bg-primary/10 text-primary border border-primary/20 text-xs font-bold active:scale-95 transition-all"
            type="button"
          >
            {t("withdraw.withdraw_all")}
          </button>
        </div>

        {/* Tài khoản ngân hàng nhận tiền */}
        <div className="mb-6">
          <label className="text-[0.8125rem] leading-[1.125rem] text-[#8b8b8b] block mb-2">
            {t("withdraw.select_account")}
          </label>

          {accounts.length === 0 ? (
            <div className="bg-[#1f1f1f] p-6 text-center border border-dashed border-white/10 flex flex-col items-center">
              <AlertCircle className="size-8 text-[#8b8b8b] mb-2" />
              <p className="text-sm text-[#d0d5da] font-semibold">{t("withdraw.no_account")}</p>
              <p className="text-xs text-[#8b8b8b] mt-1">{t("withdraw.no_account_hint")}</p>
              <Link
                href="/profile/account-settings/withdrawal-details"
                className="mt-4 px-4 py-2 bg-primary text-xs font-bold text-white flex items-center gap-x-1.5 active:scale-95 transition-all"
              >
                <Plus className="size-3.5" />
                {t("withdraw.add_account")}
              </Link>
            </div>
          ) : (
            <div className="flex flex-col gap-y-3">
              {accounts.map((account) => {
                const bank = findBank(bankList, account.bankCode);
                const isSelected = account.id === selectedAccountId;
                return (
                  <button
                    key={account.id}
                    onClick={() => setSelectedAccountId(account.id)}
                    className={`w-full flex items-center gap-x-3 bg-[#1f1f1f] px-4 py-3 text-left transition-all border ${
                      isSelected
                        ? "border-primary ring-1 ring-primary/30"
                        : "border-transparent hover:border-white/10"
                    }`}
                    type="button"
                  >
                    {bank?.logo ? (
                      <Image
                        alt=""
                        className="h-6 w-10 object-contain shrink-0"
                        height={24}
                        unoptimized
                        src={bank.logo}
                        width={40}
                      />
                    ) : (
                      <div className="h-6 w-10 bg-white/5 border border-white/10 flex items-center justify-center text-[10px] text-[#8b8b8b] font-bold shrink-0">
                        {account.bankCode}
                      </div>
                    )}

                    <div className="flex grow flex-col min-w-0">
                      <span className="text-[0.875rem] text-[#d0d5da] font-medium truncate">
                        {bank?.shortName ?? account.bankCode}
                      </span>
                      <span className="text-[0.75rem] text-[#8b8b8b] truncate">
                        {account.maskedAccountNumber}
                        {account.holderName ? ` · ${account.holderName}` : ""}
                      </span>
                    </div>

                    {account.isDefault ? (
                      <span className="bg-[#01bd8b]/10 px-2 py-0.5 text-[10px] text-[#01bd8b] shrink-0">
                        {t("withdraw.default_badge")}
                      </span>
                    ) : null}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Số tiền rút */}
        <div className="mb-6">
          <TextField
            label={t("withdraw.amount")}
            placeholder={t("withdraw.amount_placeholder")}
            value={amount}
            onChange={handleAmountChange}
            inputMode="numeric"
          />

          {/*
            Chọn số tiền nhanh.

            Mọi mốc ở đây đã được `quickAmounts` lọc để KHÔNG vượt số dư, nên không còn nhánh
            nút bị tắt: một hàng nút bấm được nào cũng được là điều người dùng mong đợi.

            Mốc đang được chọn có viền đỏ để thấy rõ con số nào đang áp dụng — không có dấu
            hiệu này thì bấm xong người dùng phải ngước lên ô nhập để đối chiếu.
          */}
          <div className="flex gap-x-3 mt-3">
            {quickAmounts.map((val) => {
              const isSelected = amount === String(val);
              return (
                <button
                  key={val}
                  onClick={() => setAmount(String(val))}
                  className={`flex-1 h-9 text-xs font-semibold flex items-center justify-center border transition-colors active:scale-98 ${
                    isSelected
                      ? "bg-primary/10 border-primary text-primary"
                      : "bg-[#1f1f1f] hover:bg-[#2a2a2a] border-white/5 text-[#d0d5da]"
                  }`}
                  type="button"
                >
                  ${val}
                </button>
              );
            })}

            {/*
              Nút "Tối đa" chỉ có nghĩa khi số dư đạt mức rút tối thiểu. Số dư $5 mà bấm Tối đa
              sẽ điền $5 rồi lập tức báo "tối thiểu $20" — một vòng lặp vô ích.
            */}
            {maxWithdrawable >= MIN_WITHDRAW_AMOUNT ? (
              <button
                onClick={handleWithdrawAll}
                className={`flex-1 h-9 text-xs font-semibold flex items-center justify-center border transition-colors active:scale-98 ${
                  amount === String(maxWithdrawable)
                    ? "bg-primary/10 border-primary text-primary"
                    : "bg-[#1f1f1f] hover:bg-[#2a2a2a] border-white/5 text-[#d0d5da]"
                }`}
                type="button"
              >
                {t("withdraw.max")}
              </button>
            ) : null}
          </div>

          {/*
            Dòng hạn mức.

            Số tối đa hiển thị là MIN(số dư, hạn mức ngày) chứ không phải hằng số `5,000`:
            người có số dư $50 đọc "tối đa 5,000 USDT" sẽ tưởng mình rút được nhiều hơn số
            tiền thực có.
          */}
          {maxWithdrawable < MIN_WITHDRAW_AMOUNT ? (
            <span className="text-[11px] text-amber-500 mt-2 block">
              {t("withdraw.err_min", { min: String(MIN_WITHDRAW_AMOUNT) })}
            </span>
          ) : (
            <span className="text-[11px] text-[#8b8b8b] mt-2 block">
              {t("withdraw.limit_hint", {
                min: String(MIN_WITHDRAW_AMOUNT),
                max: Math.min(maxWithdrawable, DAILY_MAX_AMOUNT).toLocaleString("en-US"),
              })}
            </span>
          )}
        </div>

        {/* Mật khẩu rút tiền */}
        <div className="mb-6">
          {currentUser && !currentUser.hasWithdrawalPassword ? (
            <div className="bg-amber-500/10 border border-amber-500/20 p-4 text-center flex flex-col items-center">
              <p className="text-xs text-[#8b8b8b]">{t("withdraw.err_password_not_set")}</p>
              <Link
                href="/profile/security-center"
                className="mt-3 text-xs font-bold text-primary active:scale-95 transition-all"
              >
                {t("withdraw.err_set_password")} →
              </Link>
            </div>
          ) : (
            <>
              <PasswordField
                label={t("withdraw.withdrawal_password")}
                placeholder={t("withdraw.withdrawal_password_placeholder")}
                value={withdrawalPassword}
                onChange={setWithdrawalPassword}
                maxLength={72}
              />

              {/*
                Chỉ báo kết quả kiểm ngầm.

                `aria-live="polite"` để trình đọc màn hình đọc kết quả khi nó xuất hiện:
                kết quả này quyết định nút có bấm được hay không, không đọc lên thì người
                dùng khiếm thị không biết vì sao nút vẫn không phản ứng.
              */}
              <div aria-live="polite" className="mt-2 min-h-[1rem]">
                {passwordCheck === "checking" ? (
                  <span className="flex items-center gap-x-1.5 text-[11px] text-[#8b8b8b]">
                    <Loader2 className="size-3 animate-spin" />
                    {t("withdraw.pwd_checking")}
                  </span>
                ) : passwordCheck === "valid" ? (
                  <span className="flex items-center gap-x-1.5 text-[11px] font-semibold text-[#01bd8b]">
                    <Check className="size-3.5" />
                    {t("withdraw.pwd_valid")}
                  </span>
                ) : passwordCheck === "invalid" ? (
                  <span className="flex items-center gap-x-1.5 text-[11px] font-semibold text-[#fe1616]">
                    <X className="size-3.5" />
                    {t("withdraw.pwd_invalid")}
                    {attemptsRemaining !== null ? (
                      <span className="font-normal text-[#8b8b8b]">
                        {t("withdraw.pwd_attempts_left", { count: String(attemptsRemaining) })}
                      </span>
                    ) : null}
                  </span>
                ) : passwordCheck === "locked" ? (
                  <span className="flex items-center gap-x-1.5 text-[11px] font-semibold text-amber-500">
                    <AlertCircle className="size-3.5" />
                    {t("withdraw.err_locked", { seconds: String(lockedSeconds ?? 900) })}
                  </span>
                ) : null}
              </div>
            </>
          )}
        </div>

        {/* Bảng tóm tắt giao dịch */}
        {amount && (
          <div className="bg-[#1f1f1f] p-4 flex flex-col gap-y-2.5 mb-6 text-xs text-[#8b8b8b]">
            <h3 className="font-semibold text-white mb-1">{t("withdraw.summary")}</h3>
            <div className="flex justify-between">
              <span>{t("withdraw.summary_amount")}</span>
              <span className="text-white">${parseFloat(amount) || 0.0} USDT</span>
            </div>
            <div className="flex justify-between border-t border-white/5 pt-2">
              <span>{t("withdraw.summary_account")}</span>
              <span className="text-white truncate max-w-[200px]">
                {selectedAccount ? (
                  <>
                    {findBank(bankList, selectedAccount.bankCode)?.shortName || selectedAccount.bankCode}
                    {" · "}{selectedAccount.maskedAccountNumber}
                  </>
                ) : "--"}
              </span>
            </div>
            <div className="flex justify-between border-t border-white/5 pt-2">
              <span>{t("withdraw.summary_remaining")}</span>
              <span className="text-white">${remainingBalance} USDT</span>
            </div>
          </div>
        )}

        {/* Hiển thị lỗi */}
        {error && (
          <p className="text-xs text-[#fe1616] mb-6 font-medium" role="alert">
            {error}
          </p>
        )}

        {/*
          Nút gửi lệnh — HAI TRẠNG THÁI RÕ RỆT thay vì một nút đỏ bị làm mờ:
            - Chưa đủ điều kiện: nền xám `#2a2a2a`, chữ `#5b5b5b` — trông rõ là chưa dùng được.
            - Đủ điều kiện: đỏ `#fe1616` kèm quầng sáng — thấy rõ nó VỪA MỚI sáng lên.

          `disabled-opacity` không đủ: giảm độ mờ một nút đỏ vẫn ra một nút đỏ, người dùng vẫn
          bấm và không hiểu vì sao không có gì xảy ra.
        */}
        <button
          onClick={handleWithdraw}
          disabled={submitting || !canSubmit}
          className={`h-12 w-full text-[0.9375rem] font-bold flex items-center justify-center gap-x-2 transition-all duration-300 ${
            canSubmit && !submitting
              ? "bg-[#fe1616] text-white shadow-[0_0_20px_rgba(254,22,22,0.45)] active:opacity-85"
              : "bg-[#2a2a2a] text-[#5b5b5b] cursor-not-allowed"
          }`}
          type="button"
        >
          {submitting ? (
            <>
              <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
              </svg>
              {t("withdraw.submitting")}
            </>
          ) : (
            t("withdraw.submit")
          )}
        </button>

        {/* Nói rõ còn thiếu gì để nút sáng lên. */}
        {blockingHint ? (
          <p className="mt-2.5 text-center text-[11px] text-[#8b8b8b]">{blockingHint}</p>
        ) : null}
      </main>
    </MobileShell>
  );
}
