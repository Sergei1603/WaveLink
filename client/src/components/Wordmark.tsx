import { Link } from "react-router-dom";

/** Four-bar equaliser glyph + the name. */
export function Wordmark({ to }: { to?: string }) {
  const inner = (
    <>
      <span className="wordmark-bars" aria-hidden="true">
        <i /><i /><i /><i />
      </span>
      <span className="wordmark-text">WaveLink</span>
    </>
  );

  return to
    ? <Link to={to} className="wordmark">{inner}</Link>
    : <span className="wordmark">{inner}</span>;
}
