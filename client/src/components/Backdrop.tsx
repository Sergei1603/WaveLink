import { useState } from "react";

/**
 * Ambient photo ground behind the shell. The image is optional: the CSS
 * gradients on `.backdrop` / `.auth-bg` already carry the look, and the
 * <img> layers on top of them when the file is present in `public/`.
 *
 * Drop `backdrop.*` (shell) and `backdrop-auth.*` (login/register) into
 * `client/public/` to get the photographic version from the design doc —
 * see the README there. Any of png/jpg/jpeg/webp works; the candidates
 * are tried in order and the layer stays gradient-only if none load.
 */
const EXTENSIONS = ["png", "jpg", "jpeg", "webp"];

export function Backdrop({
  name = "backdrop",
  className = "backdrop"
}: { name?: string; className?: string }) {
  const [attempt, setAttempt] = useState(0);
  const src = attempt < EXTENSIONS.length ? `/${name}.${EXTENSIONS[attempt]}` : null;

  return (
    <div className={className} aria-hidden="true">
      {src && (
        <img
          key={src}
          src={src}
          alt=""
          onError={() => setAttempt(a => a + 1)}
        />
      )}
    </div>
  );
}
