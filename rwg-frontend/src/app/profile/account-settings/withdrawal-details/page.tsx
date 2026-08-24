"use client";

import React, { useCallback, useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MobileShell } from "@/components/layout/MobileShell";
import { TopNavigationBar } from "@/components/layout/TopNavigationBar";
import {
  PasswordField,
  SelectField,
  SubmitButton,
  TextField,
} from "@/components/profile/FormField";
import { useTranslation } from "@/context/LanguageContext";
import {
  ApiError,
  addBankAccount,
  bankAccounts,
  banks,
  findBank,
  getPlayerToken,
  type BankAccount,
  type BankOption,
} from "@/lib/playerApi";

/**
 * Trang chi tiết rút tiền: liên kết tài khoản ngân hàng nhận tiền.
 *
 * Bốn ô theo đúng bản gốc: tên ngân hàng, chủ tài khoản, số tài khoản, mật khẩu rút tiền.
 *
 * THÊM PHẦN "TÀI KHOẢN ĐÃ LIÊN KẾT" mà bản gốc không có. Lý do: không có nó thì người dùng
 * bấm Lưu xong không biết đã lưu được chưa, và không thấy tài khoản nào đang nhận tiền.
 * Rút tiền luôn chuyển vào tài khoản mặc định, nên việc thấy rõ nó là quan trọng.
 *
 * MỖI NGƯỜI MỘT TÀI KHOẢN, KHÔNG TỰ GỠ ĐƯỢC:
 * Đã liên kết thì trang ẨN HẲN FORM và không hiện nút xoá; muốn đổi thì liên hệ CSKH.
 * Luật thật nằm ở `BankAccountService` bên backend (409 BANK_ACCOUNT_ALREADY_LINKED /
 * BANK_ACCOUNT_REMOVE_FORBIDDEN) — phần ẩn ở đây CHỈ để người dùng khỏi gõ xong mới
 * nhận lỗi, KHÔNG phải là biện pháp bảo vệ.
 *
 * MẬT KHẨU RÚT LÀ BẮT BUỘC khi thêm: đổi được tài khoản nhận tiền là chuyển hướng được
 * toàn bộ tiền rút, nên backend đòi xác nhận lại.
 */
export default function WithdrawalDetailsPage() {
  const router = useRouter();
  const { t } = useTranslation();

  const [loading, setLoading] = useState(true);
  const [accounts, setAccounts] = useState<BankAccount[]>([]);
  const [bankList, setBankList] = useState<BankOption[]>([]);
  // Danh sách ngân hàng lỗi thì cho gõ mã bằng tay, KHÔNG hiện ô chọn trống.
  const [bankListFailed, setBankListFailed] = useState(false);

  const [bankCode, setBankCode] = useState("");
  const [holderName, setHolderName] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [withdrawalPassword, setWithdrawalPassword] = useState("");

  const [accountNumberError, setAccountNumberError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const reloadAccounts = useCallback(async () => {
    const list = await bankAccounts();
    setAccounts(list);
  }, []);

  useEffect(() => {
    if (!getPlayerToken()) {
      router.replace("/login");
      return;
    }

    let cancelled = false;

    const load = async () => {
      // `allSettled` không `all`: danh sách ngân hàng đến từ dịch vụ ngoài, và nó lỗi thì
      // trang vẫn phải dùng được (gõ mã bằng tay). Dùng `all` sẽ để VietQR làm chết cả
      // phần hiển thị tài khoản đã liên kết.
      const [accountsResult, banksResult] = await Promise.allSettled([
        bankAccounts(),
        banks(),
      ]);

      if (cancelled) return;

      if (accountsResult.status === "fulfilled") {
        setAccounts(accountsResult.value);
      } else if (
        accountsResult.reason instanceof ApiError &&
        accountsResult.reason.status === 401
      ) {
        router.replace("/login");
        return;
      }

      if (banksResult.status === "fulfilled" && banksResult.value.length > 0) {
        setBankList(banksResult.value);
      } else {
        setBankListFailed(true);
      }

      setLoading(false);
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, [router]);

  const handleSave = useCallback(() => {
    // Số tài khoản: 6-32 chữ số. Kiểm trước ở đây để đỡ một vòng gọi mạng, nhưng luật thật
    // nằm ở `PayoutAddressValidator` bên backend vì nó còn phụ thuộc loại phương thức.
    const trimmedAccount = accountNumber.trim();
    if (!/^\d{6,32}$/.test(trimmedAccount)) {
      setAccountNumberError(t("profile.account_number_digits"));
      return;
    }
    setAccountNumberError(null);

    const save = async () => {
      setSaving(true);
      setSaveError(null);
      setSaved(false);
      try {
        await addBankAccount({
          bankCode: bankCode.trim().toUpperCase(),
          holderName: holderName.trim(),
          accountNumber: trimmedAccount,
          withdrawalPassword,
        });

        // Xoá mật khẩu khỏi state ngay sau khi lưu: không giữ nó trong bộ nhớ trang lâu
        // hơn mức cần thiết.
        setWithdrawalPassword("");
        setBankCode("");
        setHolderName("");
        setAccountNumber("");
        setSaved(true);
        await reloadAccounts();
      } catch (error) {
        // KHÔNG đẩy sang /login ở đây nữa.
        //
        // Trước đây nhánh này bắt 401 và đăng xuất người dùng. Nhưng backend từng trả 401
        // cho "sai mật khẩu rút tiền", nên gõ sai một lần là bị văng ra trang đăng nhập —
        // vừa mất dữ liệu đang gõ, vừa trông như hệ thống lỗi. Backend giờ trả 400 cho
        // trường hợp đó (WITHDRAWAL_PASSWORD_MISMATCH).
        //
        // 401 thật sự ở đây chỉ còn nghĩa "token hết hạn", mà `authedRequest` đã tự xoay
        // vòng token; nếu xoay vòng cũng thất bại thì `useEffect` lúc tải trang sẽ xử lý.
        setSaveError(describeError(error, t));
      } finally {
        setSaving(false);
      }
    };

    void save();
  }, [accountNumber, bankCode, holderName, reloadAccounts, t, withdrawalPassword]);

  /**
   * ĐÃ có tài khoản đang hoạt động hay chưa.
   *
   * Danh sách từ {@code GET /wallet/me/bank-accounts} CHỈ trả bản ghi ACTIVE (xem
   * {@code findByUserIdAndStatusOrderByCreatedAtAsc} bên backend), nên ở đây chỉ cần
   * đếm — không phải lọc theo status một lần nữa.
   */
  const hasLinkedAccount = accounts.length > 0;

  const canSave =
    Boolean(bankCode.trim()) &&
    Boolean(holderName.trim()) &&
    Boolean(accountNumber.trim()) &&
    Boolean(withdrawalPassword) &&
    !saving;

  return (
    <MobileShell
      background="plain"
      header={
        <TopNavigationBar
          backHref="/profile/account-settings"
          title={t("profile.withdrawal_details")}
        />
      }
    >
      <main className="flex grow flex-col px-5 py-4">
        {loading ? (
          <p className="mt-[50px] text-center text-[#8b8b8b]">{t("asset.loading")}</p>
        ) : hasLinkedAccount ? (
          // ĐÃ LIÊN KẾT — ẨN HẲN FORM.
          //
          // Không để form ở đó rồi vô hiệu hoá nút Lưu: người dùng sẽ gõ hết bốn ô
          // rồi mới phát hiện không bấm được, mà không hiểu vì sao. Ẩn hẳn + nói rõ
          // phải làm gì tiếp là câu trả lời đúng.
          <>
            <LinkedAccounts accounts={accounts} bankList={bankList} />

            <section className="mt-8 bg-[#1f1f1f] px-4 py-5">
              <h2 className="text-[0.9375rem] text-[#d0d5da]">
                {t("profile.account_locked_title")}
              </h2>
              <p className="mt-2 text-[0.8125rem] leading-[1.25rem] text-[#8b8b8b]">
                {t("profile.account_locked_note")}
              </p>

              <Link
                className="mt-4 inline-flex h-11 items-center justify-center bg-[#c8102e] px-6 text-[0.875rem] font-semibold text-white transition-opacity hover:opacity-90"
                href="/profile/contact-us"
                id="withdrawal-contact-support"
              >
                {t("profile.contact_support")}
              </Link>
            </section>
          </>
        ) : (
          <>
            <div className="flex flex-col gap-y-5">
              {bankListFailed ? (
                <TextField
                  label={t("profile.bank_name")}
                  maxLength={16}
                  onChange={setBankCode}
                  placeholder={t("profile.enter_x", { x: t("profile.bank_name") })}
                  value={bankCode}
                  error={t("profile.bank_list_unavailable")}
                />
              ) : (
                <SelectField
                  label={t("profile.bank_name")}
                  onChange={setBankCode}
                  options={bankList.map((bank) => ({
                    value: bank.code,
                    label: bank.shortName,
                  }))}
                  placeholder={t("profile.select_x", { x: t("profile.bank_name") })}
                  value={bankCode}
                />
              )}

              <TextField
                autoComplete="name"
                label={t("profile.account_holder")}
                maxLength={100}
                onChange={setHolderName}
                placeholder={t("profile.enter_x", { x: t("profile.account_holder") })}
                value={holderName}
              />

              <TextField
                error={accountNumberError ?? undefined}
                inputMode="numeric"
                label={t("profile.account_number")}
                maxLength={32}
                onChange={setAccountNumber}
                placeholder={t("profile.enter_x", { x: t("profile.account_number") })}
                value={accountNumber}
              />

              <PasswordField
                autoComplete="off"
                label={t("profile.withdrawal_password")}
                maxLength={72}
                onChange={setWithdrawalPassword}
                placeholder={t("profile.enter_x", { x: t("profile.withdrawal_password") })}
                value={withdrawalPassword}
              />
            </div>

            {saveError ? (
              <p className="mt-4 text-[0.8125rem] text-[#fe1616]" role="alert">
                {saveError}
              </p>
            ) : null}
            {saved ? (
              <p className="mt-4 text-[0.8125rem] text-[#01bd8b]" role="status">
                {t("profile.saved")}
              </p>
            ) : null}

            <div className="mt-8">
              <SubmitButton disabled={!canSave} onClick={handleSave}>
                {saving ? t("profile.saving") : t("profile.save")}
              </SubmitButton>
            </div>

            <LinkedAccounts accounts={accounts} bankList={bankList} />
          </>
        )}
      </main>
    </MobileShell>
  );
}

/**
 * Danh sách phương thức nhận tiền đã liên kết.
 *
 * Hiện dấu mặc định rõ ràng: tiền rút LUÔN chuyển vào phương thức này.
 *
 * KHÔNG CÓ NÚT XOÁ: người chơi không tự gỡ được tài khoản nhận tiền (backend trả 409).
 * Để nút ở đó rồi báo lỗi khi bấm là cách chắc chắn làm người dùng tưởng hệ thống hỏng.
 */
function LinkedAccounts({
  accounts,
  bankList,
}: {
  accounts: BankAccount[];
  bankList: BankOption[];
}) {
  const { t } = useTranslation();

  return (
    <section className="mt-10">
      <h2 className="text-[0.8125rem] leading-[1.125rem] text-[#8b8b8b]">
        {t("profile.linked_accounts")}
      </h2>

      {accounts.length === 0 ? (
        <p className="mt-4 text-[0.875rem] text-[#5b5b5b]">
          {t("profile.no_linked_accounts")}
        </p>
      ) : (
        <ul className="mt-4 flex flex-col gap-y-3">
          {accounts.map((account) => {
            const bank = findBank(bankList, account.bankCode);
            return (
              <li
                className="flex items-center gap-x-3 bg-[#1f1f1f] px-4 py-3"
                key={account.id}
              >
                {bank?.logo ? (
                  <Image
                    alt=""
                    className="h-6 w-10 object-contain"
                    height={24}
                    // `unoptimized` vì logo do VietQR phục vụ: đưa qua bộ tối ưu ảnh của
                    // Next đòi khai `images.remotePatterns`, mà `next.config.ts` chưa có
                    // — thiếu khai báo là lỗi lúc chạy.
                    unoptimized
                    src={bank.logo}
                    width={40}
                  />
                ) : null}

                <div className="flex grow flex-col">
                  <span className="text-[0.875rem] text-[#d0d5da]">
                    {bank?.shortName ?? account.bankCode}
                  </span>
                  <span className="text-[0.75rem] text-[#8b8b8b]">
                    {account.maskedAccountNumber}
                    {account.holderName ? ` · ${account.holderName}` : ""}
                  </span>
                </div>

                {account.isDefault ? (
                  <span className="bg-[rgba(1,189,139,0.1)] px-3 text-[0.75rem] text-[#01bd8b]">
                    {t("profile.default_account")}
                  </span>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

/**
 * Đổi lỗi từ backend thành câu người dùng hiểu được.
 *
 * Xử lý riêng hai mã hay gặp nhất ở màn này thay vì hiện thông báo chung: "mật khẩu sai" và
 * "chưa đặt mật khẩu rút" cần hai hành động khác nhau — một là gõ lại, một là phải sang
 * trung tâm bảo mật đặt mật khẩu trước.
 */
function describeError(
  error: unknown,
  t: (key: string, params?: Record<string, string | number>) => string
): string {
  if (!(error instanceof ApiError)) return t("profile.save_failed");

  if (error.code === "WITHDRAWAL_PASSWORD_NOT_SET") {
    return t("profile.withdrawal_password_not_set");
  }
  // WITHDRAWAL_PASSWORD_MISMATCH là mã hiện tại. GIỮ CẢ INVALID_CREDENTIALS để không
  // vỡ nếu frontend deploy trước backend — hai bên không lên cùng lúc.
  if (
    error.code === "WITHDRAWAL_PASSWORD_MISMATCH" ||
    error.code === "INVALID_CREDENTIALS"
  ) {
    return t("profile.withdrawal_password_wrong");
  }
  if (error.code === "BANK_ACCOUNT_ALREADY_LINKED") {
    return t("profile.already_linked");
  }
  if (error.code === "BANK_ACCOUNT_REMOVE_FORBIDDEN") {
    return t("profile.remove_forbidden");
  }
  return error.message || t("profile.save_failed");
}
