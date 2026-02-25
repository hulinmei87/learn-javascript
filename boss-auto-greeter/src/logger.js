function formatMeta(meta) {
  if (!meta) {
    return "";
  }

  if (typeof meta === "string") {
    return ` ${meta}`;
  }

  try {
    return ` ${JSON.stringify(meta)}`;
  } catch {
    return " [unserializable-meta]";
  }
}

export function createLogger(name = "boss-auto-greeter") {
  const print = (level, message, meta) => {
    const time = new Date().toISOString();
    const line = `[${time}] [${name}] [${level}] ${message}${formatMeta(meta)}`;
    if (level === "ERROR") {
      console.error(line);
      return;
    }

    if (level === "WARN") {
      console.warn(line);
      return;
    }

    console.log(line);
  };

  return {
    info(message, meta) {
      print("INFO", message, meta);
    },
    warn(message, meta) {
      print("WARN", message, meta);
    },
    error(message, meta) {
      print("ERROR", message, meta);
    }
  };
}
