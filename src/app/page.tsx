import { AuthLandingRedirect } from "@/components/auth/auth-route-guard";

export default function Home() {
  return <AuthLandingRedirect/>;
}
