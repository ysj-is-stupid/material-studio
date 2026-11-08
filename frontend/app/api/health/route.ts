export const dynamic = "force-dynamic";

export async function GET() {
  const baseUrl = process.env.JAVA_API_BASE_URL ?? "http://localhost:8080";
  try {
    const response = await fetch(`${baseUrl}/api/health`, { cache: "no-store" });
    return new Response(await response.text(), {
      status: response.status,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
  } catch {
    return Response.json({ status: "DOWN", dependency: "java-backend" }, { status: 503 });
  }
}
