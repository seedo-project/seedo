import { Navbar } from "@/components/shared/navbar";
import { AuthProvider } from "@/contexts/auth-context";

export default function MainLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthProvider>
      <Navbar />
      {children}
    </AuthProvider>
  );
}
