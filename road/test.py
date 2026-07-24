# -*- coding: utf-8 -*-
"""
Test Baidu Direction API: verify duration extraction from response JSON.
Calls walking, transit, driving APIs and prints step-level duration data.
"""
import requests
import math
import sys

# Force UTF-8 output on Windows
sys.stdout.reconfigure(encoding='utf-8')

AK = "naT7GCQRPdJs4a22oe33dsoFXPV3Oku6"
ORIGIN = "40.056871561538,116.3081418168"       # Shangdi, Beijing
DESTINATION = "39.909259844946,116.39268134834"  # Xidan, Beijing

MODES = {
    "walking": "Walk",
    "transit": "Transit",
    "driving": "Drive",
}


def fetch_direction(mode: str) -> dict | None:
    """Call Baidu directionlite API and return parsed JSON."""
    url = (
        f"https://api.map.baidu.com/directionlite/v1/{mode}"
        f"?origin={ORIGIN}&destination={DESTINATION}"
        f"&ak={AK}&coord_type=bd09ll&ret_coordtype=bd09ll&output=json"
    )
    resp = requests.get(url, timeout=15)
    if resp.status_code != 200:
        print(f"  !! HTTP {resp.status_code}")
        return None
    data = resp.json()
    if data.get("status") != 0:
        print(f"  !! API status={data.get('status')} msg={data.get('message')}")
        return None
    return data


def calc_steps_total(data: dict) -> tuple:
    """
    Sum duration from all step[].duration fields.
    Handles both flat (walking/driving) and nested (transit) step structures.
    Returns (total_seconds, [individual_durations]).
    """
    routes = data.get("result", {}).get("routes", [])
    if not routes:
        return 0, []

    all_durations = []
    for route in routes:
        steps = route.get("steps", [])
        for step_or_group in steps:
            if isinstance(step_or_group, list):
                # Transit: 2D array
                for step in step_or_group:
                    d = step.get("duration", 0)
                    if isinstance(d, (int, float)) and not math.isnan(d):
                        all_durations.append(int(d))
            elif isinstance(step_or_group, dict):
                # Walking/Driving: flat array
                d = step_or_group.get("duration", 0)
                if isinstance(d, (int, float)) and not math.isnan(d):
                    all_durations.append(int(d))

    return sum(all_durations), all_durations


def fmt_time(seconds):
    """Human-readable time string."""
    if seconds is None or (isinstance(seconds, float) and math.isnan(seconds)):
        return "N/A"
    s = int(seconds)
    if s < 60:
        return f"{s}s"
    mins = s // 60
    secs = s % 60
    if mins < 60:
        return f"{mins}m{secs}s" if secs else f"{mins}min"
    hours = mins // 60
    remain = mins % 60
    return f"{hours}h{remain}min" if remain else f"{hours}h"


def main():
    print("=" * 70)
    print("Baidu Direction API - Duration Extraction Test")
    print(f"Origin     : {ORIGIN}")
    print(f"Destination: {DESTINATION}")
    print("=" * 70)

    for mode_key, mode_name in MODES.items():
        print(f"\n{'─' * 50}")
        print(f">>> {mode_name} ({mode_key})")
        print(f"{'─' * 50}")

        data = fetch_direction(mode_key)
        if data is None:
            continue

        routes = data.get("result", {}).get("routes", [])
        if not routes:
            print("  !! No routes found")
            continue

        # ---- Method A: routes[0].duration ----
        api_duration = routes[0].get("duration")
        print(f"  [A] routes[0].duration = {api_duration}  ({fmt_time(api_duration)})")

        # ---- Method B: sum all step.duration ----
        step_total, step_list = calc_steps_total(data)
        print(f"  [B] step sum           = {step_total}  ({fmt_time(step_total)})")
        print(f"  [B] individual steps   = {step_list}")

        # ---- Compare ----
        if api_duration is not None and step_total > 0:
            diff = api_duration - step_total
            if diff == 0:
                print(f"  [=] MATCH: routes[0].duration == step sum")
            else:
                print(f"  [!=] DIFF: routes[0].duration - step sum = {diff}s ({fmt_time(abs(diff))})")

        # ---- Method C: per-step detail ----
        print(f"  [C] route count: {len(routes)}")
        for ri, route in enumerate(routes):
            steps = route.get("steps", [])
            print(f"      route[{ri}] has {len(steps)} step groups")
            for si, step_or_group in enumerate(steps):
                if isinstance(step_or_group, list):
                    group_durs = [
                        s.get("duration", "?")
                        for s in step_or_group
                    ]
                    instructions = [
                        s.get("instruction", "?")[:50]
                        for s in step_or_group
                    ]
                    print(f"        group[{si}] ({len(step_or_group)} items): durs={group_durs}")
                    for gi, inst in enumerate(instructions):
                        print(f"          [{gi}] {inst}")
                elif isinstance(step_or_group, dict):
                    d = step_or_group.get("duration", "?")
                    inst = step_or_group.get("instruction", "")[:60]
                    print(f"        step[{si}] dur={d}s  | {inst}")

        # ---- Distance ----
        distance = routes[0].get("distance")
        if distance:
            print(f"  [D] distance = {distance}m ({distance/1000:.1f}km)")

    print(f"\n{'=' * 70}")
    print("Test complete.")
    print("=" * 70)


if __name__ == "__main__":
    main()
