"use client";

import React from "react";
import Image from "next/image";
import Link from "next/link";

interface GameItem {
  id: string;
  name: string;
  image: string;
  tableId?: string;
}

const GAMES: GameItem[] = [
  {
    id: "lucky28",
    name: "Lucky 28",
    image: "/images/thumb_mbappe.jpg",
    tableId: "1",
  },
  {
    id: "british28",
    name: "British Lucky 28",
    image: "/images/thumb_ronaldo.jpg",
    tableId: "2",
  },
  {
    id: "korean28",
    name: "Korean Lucky 28",
    image: "/images/thumb_neymar.jpg",
    tableId: "3",
  },
  {
    id: "taiwantimes",
    name: "Taiwan Times",
    image: "/images/thumb_dealer.jpg",
    tableId: "4",
  },
];

export const GameGrid: React.FC = () => {
  return (
    <div className="w-full px-4 my-2">
      {/* Title */}
      <h2 className="text-base font-bold text-white mb-3 tracking-wide flex items-center gap-2">
        Trò chơi
      </h2>

      {/* Grid 2 Columns */}
      <div className="grid grid-cols-2 gap-3">
        {GAMES.map((game) => (
          <Link
            key={game.id}
            href={`/game/${game.tableId || game.id}`}
            className="group relative bg-[#18181c] border border-[#25252b] hover:border-red-600/60 rounded-xl overflow-hidden shadow-lg transition-all active:scale-[0.98] flex flex-col"
          >
            {/* Image Container */}
            <div className="relative w-full aspect-[16/10] overflow-hidden bg-black">
              <Image
                src={game.image}
                alt={game.name}
                fill
                className="object-cover group-hover:scale-105 transition-transform duration-300"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent" />
            </div>

            {/* Game Name Label */}
            <div className="p-2.5 bg-[#141417] text-center border-t border-[#222227]">
              <span className="text-xs font-bold text-gray-100 group-hover:text-red-400 transition-colors">
                {game.name}
              </span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
};
