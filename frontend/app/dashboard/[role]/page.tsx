'use client';

import { useAuthStore } from '@/stores/authStore';
import CustomerDashboard from '@/components/dashboards/CustomerDashboard';
import RetailerDashboard from '@/components/dashboards/RetailerDashboard';
import AdminDashboard from '@/components/dashboards/AdminDashboard';
import BulkBuyerDashboard from '@/components/dashboards/BulkBuyerDashboard';

const DASHBOARDS: Record<string, React.ComponentType> = {
  customer: CustomerDashboard,
  retailer: RetailerDashboard,
  admin: AdminDashboard,
  'bulk-buyer': BulkBuyerDashboard,
};

export default function DashboardPage({ params }: { params: { role: string } }) {
  const email = useAuthStore((s) => s.email);
  const Dashboard = DASHBOARDS[params.role] ?? CustomerDashboard;

  return (
    <div className="mx-auto max-w-5xl px-6 py-12">
      <h1 className="font-display text-3xl font-bold text-ink">
        Welcome back{email ? `, ${email.split('@')[0]}` : ''}
      </h1>
      <div className="mt-8">
        <Dashboard />
      </div>
    </div>
  );
}
