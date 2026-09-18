import { NextRequest, NextResponse } from 'next/server';
import { jwtDecode } from 'jwt-decode';

interface DecodedToken {
  roles: string[];
  exp: number;
}

const PROTECTED_PREFIXES = ['/checkout', '/dashboard'];
const ROLE_DASHBOARD_MAP: Record<string, string> = {
  ADMIN: 'admin',
  RETAILER: 'retailer',
  BULK_BUYER: 'bulk-buyer',
  CUSTOMER: 'customer',
};

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  const needsAuth = PROTECTED_PREFIXES.some((p) => pathname.startsWith(p));
  if (!needsAuth) return NextResponse.next();

  // The Zustand authStore persists to localStorage client-side, which
  // middleware (running at the edge) can't read directly. We mirror the
  // token into a cookie on login (see AuthProvider) specifically so
  // middleware can enforce route protection server-side before the page
  // ever renders.
  const token = request.cookies.get('ecommerce_access_token')?.value;

  if (!token) {
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('redirect', pathname);
    return NextResponse.redirect(loginUrl);
  }

  try {
    const decoded = jwtDecode<DecodedToken>(token);
    if (decoded.exp * 1000 < Date.now()) {
      const loginUrl = new URL('/login', request.url);
      return NextResponse.redirect(loginUrl);
    }

    if (pathname.startsWith('/dashboard/')) {
      const requestedRole = pathname.split('/')[2];
      const userRole = decoded.roles?.[0];
      const allowedSegment = ROLE_DASHBOARD_MAP[userRole] ?? 'customer';
      if (requestedRole !== allowedSegment && userRole !== 'ADMIN') {
        return NextResponse.redirect(new URL(`/dashboard/${allowedSegment}`, request.url));
      }
    }
  } catch {
    return NextResponse.redirect(new URL('/login', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/checkout/:path*', '/dashboard/:path*'],
};
