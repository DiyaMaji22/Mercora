import type { Metadata } from 'next';
import './globals.css';
import NavBar from '@/components/NavBar';
import AuthProvider from '@/components/AuthProvider';

export const metadata: Metadata = {
  title: 'Flashmarket',
  description: 'Live-stock flash sales, built for speed.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>
          <NavBar />
          <main className="min-h-screen bg-paper">{children}</main>
        </AuthProvider>
      </body>
    </html>
  );
}
