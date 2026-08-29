import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Input } from "@heroui/input";
import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useNavigate, useSearchParams } from "react-router-dom";

import { registerUser } from "@/api";
import { title } from "@/components/primitives";
import DefaultLayout from "@/layouts/default";

export default function RegisterPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState({ user: "", pwd: "", confirmPwd: "", inviteCode: "" });

  useEffect(() => {
    const invite = searchParams.get("invite");
    if (invite) setForm((prev) => ({ ...prev, inviteCode: invite }));
  }, [searchParams]);

  const submit = async () => {
    if (!form.user.trim()) return toast.error("请输入用户名");
    if (form.pwd.length < 6) return toast.error("密码长度至少6位");
    if (form.pwd !== form.confirmPwd) return toast.error("两次输入密码不一致");
    setLoading(true);
    try {
      const res = await registerUser({ user: form.user.trim(), pwd: form.pwd, inviteCode: form.inviteCode.trim() || undefined });
      if (res.code !== 0) return toast.error(res.msg || "注册失败");
      toast.success("注册成功，请登录");
      navigate("/", { replace: true });
    } catch (error) {
      toast.error("注册请求失败");
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Enter" && !loading) submit();
  };

  return (
    <DefaultLayout>
      <section className="flex min-h-[calc(100dvh-120px)] items-center justify-center px-4 py-8 pb-20">
        <Card className="w-full max-w-md">
          <CardHeader className="pb-0 pt-6 px-6 flex-col items-center"><h1 className={title({ size: "sm" })}>注册</h1><p className="text-small text-default-500 mt-2">创建账号后即可购买套餐</p></CardHeader>
          <CardBody className="px-6 py-6 space-y-4">
            <Input label="用户名" value={form.user} onChange={(e) => setForm({ ...form, user: e.target.value })} onKeyDown={handleKeyDown} />
            <Input label="密码" type="password" value={form.pwd} onChange={(e) => setForm({ ...form, pwd: e.target.value })} onKeyDown={handleKeyDown} />
            <Input label="确认密码" type="password" value={form.confirmPwd} onChange={(e) => setForm({ ...form, confirmPwd: e.target.value })} onKeyDown={handleKeyDown} />
            <Input label="邀请码" value={form.inviteCode} onChange={(e) => setForm({ ...form, inviteCode: e.target.value })} onKeyDown={handleKeyDown} />
            <Button color="primary" size="lg" isLoading={loading} onClick={submit}>注册</Button>
            <Button variant="light" onClick={() => navigate("/")}>返回登录</Button>
          </CardBody>
        </Card>
      </section>
    </DefaultLayout>
  );
}
