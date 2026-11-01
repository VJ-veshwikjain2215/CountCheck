(() => {
  const form = document.querySelector("#count-form");
  const workspace = (form || document.querySelector("[data-saved-key]"))
    ?.dataset.workspace;
  const key = "countcheck.pending." + workspace;
  const store = {
    get() {
      try {
        return JSON.parse(sessionStorage.getItem(key));
      } catch {
        return null;
      }
    },
    set(v) {
      try {
        sessionStorage.setItem(key, JSON.stringify(v));
      } catch {}
    },
    clear() {
      try {
        sessionStorage.removeItem(key);
      } catch {}
    },
  };
  const saved = document.querySelector("[data-saved-key]");
  if (saved && store.get()?.requestId === saved.dataset.savedKey) store.clear();
  document.querySelector("#signout")?.addEventListener("submit", (e) => {
    try {
      sessionStorage.removeItem(
        "countcheck.pending." + e.target.dataset.workspace,
      );
    } catch {}
  });
  if (!form) return;
  const a = form.elements.recorded,
    b = form.elements.physical,
    out = document.querySelector("#difference"),
    caption = document.querySelector("#difference-caption");
  function update() {
    if (
      !/^(0|[1-9][0-9]{0,5})$/.test(a.value) ||
      !/^(0|[1-9][0-9]{0,5})$/.test(b.value) ||
      +a.value > 100000 ||
      +b.value > 100000
    ) {
      out.textContent = "—";
      caption.textContent = "Enter valid counts to compare.";
      return;
    }
    const n = Number(b.value) - Number(a.value);
    out.textContent = n > 0 ? "+" + n : n < 0 ? "−" + Math.abs(n) : "0";
    caption.textContent =
      n === 0
        ? "Your counts match."
        : n < 0
          ? "Fewer items than recorded."
          : "More items than recorded.";
  }
  [a, b].forEach((el) => el.addEventListener("input", update));
  form.elements.productId.addEventListener("change", (e) => {
    a.value = e.target.selectedOptions[0]?.dataset.recorded ?? "";
    update();
  });
  const pending = store.get();
  if (pending && typeof pending === "object" && pending.requestId) {
    document.querySelector("#restore").hidden = false;
    document.querySelector("#restore-button").addEventListener("click", () => {
      if (
        typeof pending.productId === "string" &&
        ![...form.elements.productId.options].some(
          (o) => o.value === pending.productId,
        )
      )
        form.elements.productId.add(
          new Option(
            typeof pending.productLabel === "string"
              ? pending.productLabel
              : "Restored product",
            pending.productId,
          ),
        );
      for (const f of [
        "requestId",
        "productId",
        "recorded",
        "physical",
        "note",
      ])
        if (typeof pending[f] === "string") form.elements[f].value = pending[f];
      document.querySelector("#restore").hidden = true;
      update();
    });
  }
  form.addEventListener("submit", () => {
    store.set({
      ...Object.fromEntries(
        ["requestId", "productId", "recorded", "physical", "note"].map((k) => [
          k,
          form.elements[k].value,
        ]),
      ),
      productLabel:
        form.elements.productId.selectedOptions[0]?.textContent ??
        "Restored product",
    });
  });
  update();
})();
