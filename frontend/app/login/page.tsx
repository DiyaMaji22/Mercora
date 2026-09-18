'use client';

import { useForm } from 'react-hook-form';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import Link from 'next/link';
import apiClient from '@/lib/apiClient';
import { useAuthStore } from '@/stores/authStore';

interface LoginForm {
  email: string;
  password: string;
}

function LoginFormInner() {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginForm>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const login = useAuthStore((s) => s.login);
  const [serverError, setServerError] = useState<string | null>(null);

  const onSubmit = async (data: LoginForm) => {
    setServerError(null);
    try {
      const res = await apiClient.post('/api/v1/auth/login', data);
      login(res.data.accessToken);
      router.push(searchParams.get('redirect') || '/products');
    } catch (e: any) {
      setServerError(e.response?.data?.message || 'Invalid email or password.');
    }
  };

  return (
    <div className="mx-auto max-w-sm px-6 py-24">
      <h1 className="font-display text-2xl font-bold text-ink">Sign in</h1>

      <form onSubmit={handleSubmit(onSubmit)} className="mt-8 space-y-4">
        <div>
          <label className="text-sm font-semibold text-ink">Email</label>
          <input
            type="email"
            {...register('email', { required: 'Email is required' })}
            className="mt-1 w-full rounded border border-ink/20 px-3 py-2 focus:border-signal focus:outline-none"
          />
          {errors.email && <p className="mt-1 text-sm text-danger">{errors.email.message}</p>}
        </div>

        <div>
          <label className="text-sm font-semibold text-ink">Password</label>
          <input
            type="password"
            {...register('password', { required: 'Password is required' })}
            className="mt-1 w-full rounded border border-ink/20 px-3 py-2 focus:border-signal focus:outline-none"
          />
          {errors.password && <p className="mt-1 text-sm text-danger">{errors.password.message}</p>}
        </div>

        {serverError && <p className="text-sm text-danger">{serverError}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded bg-ink px-6 py-3 font-semibold text-paper hover:bg-signal hover:text-ink disabled:opacity-60"
        >
          {isSubmitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p className="mt-6 text-sm text-muted">
        New here?{' '}
        <Link href="/register" className="font-semibold text-ink hover:text-signal-dim">
          Create an account
        </Link>
      </p>
    </div>
  );
}

export default function LoginPage() {
  // useSearchParams() opts this subtree out of static rendering, so it must
  // sit inside a Suspense boundary or `next build` fails prerendering.
  return (
    <Suspense fallback={<div className="px-6 py-24 text-center text-muted">Loading…</div>}>
      <LoginFormInner />
    </Suspense>
  );
}
