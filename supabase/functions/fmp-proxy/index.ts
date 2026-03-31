import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const CACHE_TTL_MINUTES = 30;

serve(async (req) => {
  try {
    const { symbol, endpoint } = await req.json();

    if (!symbol || !endpoint) {
      return new Response(
        JSON.stringify({ error: "symbol and endpoint are required" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    // 1) 캐시 히트 확인
    const { data: cached } = await supabase
      .from("fmp_cache")
      .select("response_json, fetched_at")
      .eq("symbol", symbol)
      .eq("endpoint", endpoint)
      .single();

    if (cached) {
      const ageMinutes =
        (Date.now() - new Date(cached.fetched_at).getTime()) / 60000;
      if (ageMinutes < CACHE_TTL_MINUTES) {
        return new Response(JSON.stringify(cached.response_json), {
          headers: { "Content-Type": "application/json" },
        });
      }
    }

    // 2) 캐시 미스 → FMP 호출
    const fmpApiKey = Deno.env.get("FMP_API_KEY");
    const fmpUrl = `https://financialmodelingprep.com/api/v3/${endpoint}/${symbol}?apikey=${fmpApiKey}`;
    const fmpResp = await fetch(fmpUrl);
    const fmpData = await fmpResp.json();

    // 3) 캐시 저장 (UPSERT)
    await supabase.from("fmp_cache").upsert(
      {
        symbol,
        endpoint,
        response_json: fmpData,
        fetched_at: new Date().toISOString(),
      },
      { onConflict: "symbol,endpoint" }
    );

    return new Response(JSON.stringify(fmpData), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    return new Response(
      JSON.stringify({ error: (e as Error).message }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
