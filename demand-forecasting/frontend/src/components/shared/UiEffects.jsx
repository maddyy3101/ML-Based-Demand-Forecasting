import { useEffect, useRef } from "react";

function supportsFinePointer() {
  return window.matchMedia && window.matchMedia("(pointer: fine)").matches;
}

export default function UiEffects() {
  const dotRef = useRef(null);
  const ringRef = useRef(null);

  useEffect(() => {
    if (typeof window === "undefined") return undefined;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("pg-reveal-visible");
          }
        });
      },
      { threshold: 0.14, rootMargin: "0px 0px -8% 0px" }
    );

    const observed = new WeakSet();
    const selectors = ".pg-page-flow > *, .pg-card, .pg-hero-note, .pg-hero-left";

    const scan = () => {
      document.querySelectorAll(selectors).forEach((node) => {
        if (observed.has(node)) return;
        observed.add(node);
        node.classList.add("pg-reveal-ready");
        observer.observe(node);
      });
    };

    scan();

    const mutationObserver = new MutationObserver(() => scan());
    mutationObserver.observe(document.body, { childList: true, subtree: true });

    return () => {
      mutationObserver.disconnect();
      observer.disconnect();
    };
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") return undefined;
    if (!supportsFinePointer()) return undefined;

    const dot = dotRef.current;
    const ring = ringRef.current;
    if (!dot || !ring) return undefined;

    let mouseX = window.innerWidth / 2;
    let mouseY = window.innerHeight / 2;
    let ringX = mouseX;
    let ringY = mouseY;
    let ringScale = 1;
    let dotScale = 1;
    let rafId = null;

    const updateCursor = () => {
      // Slightly faster follow response while keeping smooth trailing.
      ringX += (mouseX - ringX) * 0.26;
      ringY += (mouseY - ringY) * 0.26;
      dot.style.transform = `translate3d(${mouseX}px, ${mouseY}px, 0) scale(${dotScale})`;
      ring.style.transform = `translate3d(${ringX}px, ${ringY}px, 0) scale(${ringScale})`;
      rafId = requestAnimationFrame(updateCursor);
    };

    const setHoverState = (enabled) => {
      ring.classList.toggle("is-hover", enabled);
      dot.classList.toggle("is-hover", enabled);
      ringScale = enabled ? 1.28 : 1;
      dotScale = enabled ? 1.18 : 1;
    };

    const onMove = (event) => {
      mouseX = event.clientX;
      mouseY = event.clientY;
      dot.classList.remove("is-hidden");
      ring.classList.remove("is-hidden");
    };

    const onLeave = () => {
      dot.classList.add("is-hidden");
      ring.classList.add("is-hidden");
    };

    const onEnter = () => {
      dot.classList.remove("is-hidden");
      ring.classList.remove("is-hidden");
    };

    const onPointerDown = () => {
      ring.classList.add("is-pressed");
      ringScale = 0.9;
    };
    const onPointerUp = () => {
      ring.classList.remove("is-pressed");
      ringScale = ring.classList.contains("is-hover") ? 1.28 : 1;
    };

    const onPointerOver = (event) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      const interactive = target.closest("a,button,input,textarea,select,[role='button'],.pg-card,.pg-top-nav-link");
      setHoverState(Boolean(interactive));
    };

    dot.classList.remove("is-hidden");
    ring.classList.remove("is-hidden");

    document.addEventListener("mousemove", onMove);
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("pointerup", onPointerUp);
    document.addEventListener("mouseover", onPointerOver);
    window.addEventListener("mouseout", onLeave);
    window.addEventListener("mouseover", onEnter);

    rafId = requestAnimationFrame(updateCursor);

    return () => {
      if (rafId) cancelAnimationFrame(rafId);
      document.removeEventListener("mousemove", onMove);
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("pointerup", onPointerUp);
      document.removeEventListener("mouseover", onPointerOver);
      window.removeEventListener("mouseout", onLeave);
      window.removeEventListener("mouseover", onEnter);
    };
  }, []);

  return (
    <>
      <span ref={ringRef} className="pg-cursor-ring is-hidden" aria-hidden="true" />
      <span ref={dotRef} className="pg-cursor-dot is-hidden" aria-hidden="true" />
    </>
  );
}
