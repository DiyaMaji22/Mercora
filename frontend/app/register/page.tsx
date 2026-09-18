'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import Link from 'next/link';
import apiClient from '@/lib/apiClient';
import { useAuthStore } from '@/stores/authStore';

interface RegisterForm {
  fullName: string;
  email: string;
  password: string;
  role: 'CUSTOMER' | 'RETAILER' | 'BULK_BUYER';
}

export default function RegisterPage() {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<RegisterForm>({
    defaultValues: { role: 'CUSTOMER' },
  });
  const router = useRouter();
  const login = useAuthStore((s) => s.login);
  const [serverError, setServerError] = useState<string | null>(null);

  const onSubmit = async (data: RegisterForm) => {
    setServerError(null);
    try {
      const res = await apiClient.post('/api/v1/auth/register', data);
      login(res.data.accessToken);
      router.push('/products');
    } catch (e: any) {
      setServerError(e.response?.data?.message || 'Could not create your account.');
    }
  };

  return (
    <div className="mx-auto max-w-sm px-6 py-24">
      <h1 className="font-display text-2xl font-bold text-ink">Create an account</h1>

      <form onSubmit={handleSubmit(onSubmit)} className="mt-8 space-y-4">
        <div>
          <label className="text-sm font-semibold text-ink">Full name</label>
          <input
            {...register('fullName', { required: 'Name is required' })}
            className="mt-1 w-full rounded border border-ink/20 px-3 py-2 focus:border-signal focus:outline-none"
          />
          {errors.fullName && <p className="mt-1 text-sm text-danger">{errors.fullName.message}</p>}
        </div>

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
            {...register('password', {
              required: 'Password is required',
              minLength: { value: 8, message: 'Must be at least 8 characters' },
            })}
            className="mt-1 w-full rounded border border-ink/20 px-3 py-2 focus:border-signal focus:outline-none"
          />
          {errors.password && <p className="mt-1 text-sm text-danger">{errors.password.message}</p>}
        </div>

        <div>
          <label className="text-sm font-semibold text-ink">Account type</label>
          <select
            {...register('role')}
            className="mt-1 w-full rounded border border-ink/20 px-3 py-2 focus:border-signal focus:outline-none"
          >
            <option value="CUSTOMER">Customer</option>
            <option value="RETAILER">Retailer</option>
            <option value="BULK_BUYER">Bulk buyer</option>
          </select>
        </div>

        {serverError && <p className="text-sm text-danger">{serverError}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded bg-signal px-6 py-3 font-semibold text-ink hover:bg-signal-dim disabled:opacity-60"
        >
          {isSubmitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="mt-6 text-sm text-muted">
        Already have an account?{' '}
        <Link href="/login" className="font-semibold text-ink hover:text-signal-dim">
          Sign in
        </Link>
      </p>
    </div>
  );
}
