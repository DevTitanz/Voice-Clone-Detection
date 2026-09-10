from slowapi import Limiter
from slowapi.util import get_remote_address
from starlette.requests import Request


def rate_limit_key_func(request: Request) -> str:
    """Derive rate limiting key: prefers authenticated user sub, falls back to remote IP."""
    # Check if authorization bearer is present to avoid proxy IP collisions for authenticated users
    auth_header = request.headers.get("Authorization")
    if auth_header and auth_header.startswith("Bearer "):
        token = auth_header.split(" ", 1)[1]
        # Return a sanitized token hash or suffix as the key
        return f"tok_{hash(token) % 10000000}"
    return get_remote_address(request) or "127.0.0.1"


limiter = Limiter(key_func=rate_limit_key_func)
