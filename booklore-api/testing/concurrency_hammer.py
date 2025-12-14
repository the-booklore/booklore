import requests
import concurrent.futures
import sys
import time
import random

# concurrency_hammer.py
# Simulates race conditions on the finalize endpoint.

BASE_URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
FINALIZE_ENDPOINT = f"{BASE_URL}/api/v1/bookdrop/imports/finalize"
CONCURRENCY = 20

def finalize_request(file_id):
    payload = {
        "files": [
            {
                "fileId": file_id,
                "libraryId": 1,
                "pathId": 1,
                "metadata": {"title": f"Concurrent Book {file_id}"}
            }
        ]
    }
    try:
        # Simulate slight jitter to maximize race condition potential
        time.sleep(random.random() * 0.1)
        response = requests.post(FINALIZE_ENDPOINT, json=payload)
        return response.status_code, file_id
    except Exception as e:
        return str(e), file_id

print(f"Starting Concurrency Hammer Test against {FINALIZE_ENDPOINT}")
print(f"Threads: {CONCURRENCY}")

# Note: In a real integration test, we would first need to seed the 'bookdrop' 
# with files to finalize. For this simulation, we assume the IDs exist or 
# we are testing the locking mechanism's ability to handle the requests 
# (even if they fail due to missing files, they shouldn't cause DB locks/500s).
# If the app handles concurrency correctly, it should return 200 or 404/400, 
# but NEVER 500 or timeout.

with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENCY) as executor:
    futures = [executor.submit(finalize_request, i) for i in range(1, CONCURRENCY + 1)]
    
    results = []
    for future in concurrent.futures.as_completed(futures):
        status, file_id = future.result()
        results.append((status, file_id))
        print(f"Req {file_id}: Status {status}")

# Analyze results
failures = [r for r in results if r[0] == 500 or str(r[0]).startswith("5")]
if failures:
    print(f"FAILURE: {len(failures)} requests failed with Server Error.")
    sys.exit(1)
else:
    print("SUCCESS: No 500 errors detected under load.")
    sys.exit(0)
