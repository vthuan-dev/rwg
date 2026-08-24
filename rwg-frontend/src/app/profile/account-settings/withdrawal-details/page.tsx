"use client";

import React, { useCallback, useEffect, useState } from "react";
import Image from "next/image";
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
  removeBankAccount,
  type BankAccount,
  type BankOption,
} from "@/lib/playerApi";

/**
 * Trang chi tiết rút tiền: liên kết tài khoản ngân hàng nhận tiền.
 *
 * Bốn ô theo đúng bản gốc: tên ngân hàng, chủ tài khoản, số tài khoản, mật khẩu rút tiền.
 *
 * THÊM PHẦN "TÀI KHOẢN ĐÃ LIÊN KẾT" mà bản gốc không có. Lý do: không có nó thì người dùng
 * bấm Lưu xong không biết đã lưu được chưa, không thấy tài khoản nào đang là mặc định để
 * nhận tiền, và không gỡ được tài khoản gõ sai. Rút tiền luôn chuyển vào tài khoản mặc
 * định, nên việc thấy rõ nó là quan trọng chứ không phải tiện lợi thêm.
 *
 * MẬT KHẨU RÚT LÀ BẮT BUỘC: đổi được tài khoản nhận tiền là chuyển hướng được toàn bộ tiền
 * rút, nên backend đòi xác nhận lại (xem `BankAccountService.requireWithdrawalPassword`).
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
  const [removingId, setRemovingId] = useState<string | null>(null);

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
        setSaveError(describeError(error, t));
        if (error instanceof ApiError && error.status === 401) {
          router.replace("/login");
        }
      } finally {
        setSaving(false);
      }
    };

    void save();
  }, [accountNumber, bankCode, holderName, reloadAccounts, router, t, withdrawalPassword]);

  const handleRemove = useCallback(
    (id: string) => {
      const remove = async () => {
        setRemovingId(id);
        setSaveError(null);
        try {
          await removeBankAccount(id);
          await reloadAccounts();
        } catch (error) {
          setSaveError(describeError(error, t));
        } finally {
          setRemovingId(null);
        }
      };

      void remove();
    },
    [reloadAccounts, t]
  );

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

            <LinkedAccounts
              accounts={accounts}
              bankList={bankList}
              onRemove={handleRemove}
              removingId={removingId}
            />
          </>
        )}
      </main>
    </MobileShell>
  );
}

/**
 * Danh sách phương thức nhận tiền đã liên kết.
 *
 * Hiện dấu mặc định rõ ràng: tiền rút LUÔN chuyển vào phương thức mặc định, nên người dùng
 * có nhiều tài khoản mà không biết cái nào đang nhận tiền là một chỗ dễ mất tiền.
 */
function LinkedAccounts({
  accounts,
  bankList,
  onRemove,
  removingId,
}: {
  accounts: BankAccount[];
  bankList: BankOption[];
  onRemove: (id: string) => void;
  removingId: string | null;
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

                <button
                  className="text-[0.75rem] text-[#fe1616] disabled:opacity-50"
                  disabled={removingId === account.id}
                  onClick={() => onRemove(account.id)}
                  type="button"
                >
                  {removingId === account.id
                    ? t("profile.removing")
                    : t("profile.remove")}
                </button>
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
  if (error.code === "INVALID_CREDENTIALS") {
    return t("profile.withdrawal_password_wrong");
  }
  return error.message || t("profile.save_failed");
}
